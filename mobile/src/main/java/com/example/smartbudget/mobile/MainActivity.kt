package com.example.smartbudget.mobile
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import com.example.smartbudget.*
import com.example.smartbudget.WearableConstants as W
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity(), MessageClient.OnMessageReceivedListener {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
 private val prefs by lazy { getSharedPreferences("budget", MODE_PRIVATE) }
 private var limit = 100000L
 private var revision = 0L
 private var expenses = JSONArray()
 private lateinit var summary: TextView
 private lateinit var history: TextView
 private lateinit var status: TextView
 private lateinit var progress: ProgressBar
 private fun spent(): Long = (0 until expenses.length()).sumOf { expenses.getJSONObject(it).getLong("cents") }
 override fun onCreate(b: Bundle?) {
  super.onCreate(b)
  limit=prefs.getLong("limit",100000); revision=prefs.getLong("revision",0); expenses=JSONArray(prefs.getString("expenses","[]")!!)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,32,32,32); setBackgroundColor(Color.rgb(15,32,37)) }
  val scroll=ScrollView(this); scroll.addView(root); setContentView(scroll)
  fun label(t:String,size:Float=18f)=TextView(this).apply { text=t; textSize=size; setTextColor(Color.WHITE); root.addView(this) }
  label("SmartBudget",30f); summary=label("",24f)
  progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.max=100; root.addView(progress)
  val input=EditText(this).apply { hint="Monthly budget (MXN)"; inputType=8194; setText((limit/100.0).toString()); root.addView(this) }
  fun button(t:String,action:()->Unit){ root.addView(Button(this).apply { text=t; setOnClickListener { action() } }) }
  button("Update budget") { val value=parseCents(input.text.toString()); if(value==null) status.text="Enter a valid amount" else { limit=value; save(); render(); sync() } }
  button("Sync to watch") { sync() }
  status=label("Open both apps on paired devices",14f); label("Recent expenses",22f); history=label(""); render()
 }
 private fun render(){ summary.text="Remaining: ${money(limit-spent())}\nBudget: ${money(limit)} | Spent: ${money(spent())}"; progress.progress=(spent()*100/limit).coerceIn(0,100).toInt(); history.text=(0 until expenses.length()).reversed().joinToString("\n") { val e=expenses.getJSONObject(it); "${e.getString("category")}  −${money(e.getLong("cents"))}" }.ifEmpty { "No expenses yet" } }
 private fun save(){ revision++; prefs.edit().putLong("limit",limit).putLong("revision",revision).putString("expenses",expenses.toString()).commit() }
 // DataClient publishes the authoritative snapshot; the watch never computes the ledger.
 private suspend fun publish(){ val req=PutDataMapRequest.create(W.BUDGET); req.dataMap.putLong(W.KEY_LIMIT,limit); req.dataMap.putLong(W.KEY_SPENT,spent()); req.dataMap.putLong(W.KEY_REVISION,revision); Wearable.getDataClient(this).putDataItem(req.asPutDataRequest().setUrgent()).await() }
 private fun sync(){ scope.launch { try { publish(); status.text="Budget published" } catch(e:Exception){ status.text="Sync failed: ${e.message}" } } }
 override fun onStart(){ super.onStart(); scope.launch { try { Wearable.getMessageClient(this@MainActivity).addListener(this@MainActivity).await(); publish() } catch(e:Exception){ status.text="Data Layer unavailable" } } }
 override fun onStop(){ Wearable.getMessageClient(this).removeListener(this); super.onStop() }
 override fun onDestroy(){ scope.cancel(); super.onDestroy() }
 // MessageClient separates expense commands from snapshot requests by path.
 override fun onMessageReceived(event: MessageEvent){ scope.launch { when(event.path){
 W.REQUEST -> sync()
 W.EXPENSE -> { var id=""; var ok=false; var detail="Invalid expense"; try {
  val e=Expense.decode(event.data); id=e.id; require(e.cents in 1..100000000L && e.category in listOf("Food","Transport","Shopping") && e.id.isNotBlank())
  // A retry with the same UUID is acknowledged without charging twice.
  if((0 until expenses.length()).none { expenses.getJSONObject(it).getString("id")==e.id }) { expenses.put(e.json()); save(); render() }
  ok=true; detail="Saved"; status.text="Expense saved from watch"
 } catch(e:Exception){ status.text="Invalid expense" }
 try { val ack=JSONObject().put("id",id).put("ok",ok).put("detail",detail); Wearable.getMessageClient(this@MainActivity).sendMessage(event.sourceNodeId,W.ACK,ack.toString().toByteArray()).await(); if(ok) publish() } catch(e:Exception){ status.text="Reply/sync failed; retry from watch" }
 }
 } } }
}
