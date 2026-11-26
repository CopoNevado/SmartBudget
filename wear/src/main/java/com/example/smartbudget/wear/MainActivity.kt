package com.example.smartbudget.wear
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.example.smartbudget.*
import com.example.smartbudget.WearableConstants as W
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class MainActivity : Activity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var limit=0L; private var spent=0L; private var revision=-1L
 private var pending:Expense?=null
 private var sending=false
 private lateinit var balance:TextView; private lateinit var status:TextView; private lateinit var progress:ProgressBar
 override fun onCreate(b:Bundle?){ super.onCreate(b)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(26,24,26,24); setBackgroundColor(Color.rgb(15,32,37)) }
  val scroll=ScrollView(this); scroll.addView(root); setContentView(scroll)
  fun label(t:String,size:Float)=TextView(this).apply { text=t; textSize=size; gravity=Gravity.CENTER; setTextColor(Color.WHITE); root.addView(this) }
  label("SmartBudget",18f); balance=label("Waiting for budget",22f); progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.max=100; root.addView(progress,120,12)
  val amounts=listOf(25L,50L,100L,250L); val amount=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,amounts.map { money(it*100) }); setSelection(1); root.addView(this) }
  val categories=listOf("Food","Transport","Shopping"); val category=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,categories); root.addView(this) }
  root.addView(Button(this).apply { text="Add expense"; setOnClickListener { if(pending==null) pending=Expense.create(amounts[amount.selectedItemPosition]*100,categories[category.selectedItemPosition]); sendExpense() } })
  root.addView(Button(this).apply { text="Refresh budget"; setOnClickListener { requestBudget() } }); status=label("Connect phone",12f)
 }
 private suspend fun phoneNode(): String { val nodes=Wearable.getNodeClient(this).connectedNodes.await(); return (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id ?: error("No paired phone") }
 // A message carries the JSON payload; delivery is not the same as a successful save.
 private fun sendExpense(){ if(sending) return; val e=pending?:return; sending=true; scope.launch { try { status.text="Sending ${money(e.cents)}..."; Wearable.getMessageClient(this@MainActivity).sendMessage(phoneNode(),W.EXPENSE,e.bytes()).await(); delay(5000); if(pending?.id==e.id) status.text="No confirmation. Tap Add to retry." } catch(ex:Exception){ status.text="Not sent. Tap Add to retry." } finally { sending=false } } }
 private fun requestBudget(){ scope.launch { try { Wearable.getMessageClient(this@MainActivity).sendMessage(phoneNode(),W.REQUEST,byteArrayOf()).await() } catch(e:Exception){ status.text="Phone unavailable" } } }
 override fun onMessageReceived(event:MessageEvent){ if(event.path!=W.ACK)return; scope.launch { try { val j=JSONObject(event.data.toString(Charsets.UTF_8)); if(j.getString("id")!=pending?.id)return@launch; status.text=if(j.getBoolean("ok")) "SUCCESS: Expense saved" else "Expense rejected"; pending=null } catch(e:Exception){ status.text="Invalid confirmation" } } }
 // DataClient handles persistent synchronized budget snapshots, separately from commands.
 private fun applyItem(item:DataItem){ if(item.uri.path!=W.BUDGET)return; val m=DataMapItem.fromDataItem(item).dataMap; val r=m.getLong(W.KEY_REVISION); if(r<revision)return; revision=r; limit=m.getLong(W.KEY_LIMIT); spent=m.getLong(W.KEY_SPENT); balance.text="${money(limit-spent)}\nRemaining"; progress.progress=if(limit>0)(spent*100/limit).coerceIn(0,100).toInt() else 0 }
 override fun onDataChanged(events:DataEventBuffer){ for(event in events){ if(event.type==DataEvent.TYPE_CHANGED && event.dataItem.uri.path==W.BUDGET){ val item=event.dataItem.freeze(); scope.launch { applyItem(item) } } } }
 override fun onStart(){ super.onStart(); scope.launch { try { Wearable.getMessageClient(this@MainActivity).addListener(this@MainActivity).await(); Wearable.getDataClient(this@MainActivity).addListener(this@MainActivity).await(); val items=Wearable.getDataClient(this@MainActivity).dataItems.await(); try { for(item in items) applyItem(item) } finally { items.release() }; requestBudget() } catch(e:Exception){ status.text="Data Layer unavailable" } } }
 override fun onStop(){ Wearable.getMessageClient(this).removeListener(this); Wearable.getDataClient(this).removeListener(this); super.onStop() }
 override fun onDestroy(){ scope.cancel(); super.onDestroy() }
}
