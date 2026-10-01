package com.dims.mymoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val bg = Color(0xFF0B1020)
private val panel = Color(0xFF171F33)
private val accent = Color(0xFF63E6BE)
private data class Tx(val date:String,val name:String,val kind:String,val category:String,val account:String,val currency:String,val amount:Double,val rate:Double,val target:String="")
private fun money(v:Double,c:String)= (if(c=="MYR") "RM " else "Rp ") + NumberFormat.getNumberInstance(Locale("id","ID")).format(v)
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val prefs=getSharedPreferences("mymoney",MODE_PRIVATE)
  setContent {
   var rate by remember { mutableStateOf(prefs.getFloat("rate",3800f).toString()) }
   var txs by remember { mutableStateOf(load(prefs)) }
   var tab by remember { mutableStateOf("Ringkasan") }
   var show by remember { mutableStateOf(false) }
   var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
   var kind by remember { mutableStateOf("Pengeluaran") }; var curr by remember { mutableStateOf("MYR") }
   var account by remember { mutableStateOf("Cash Ringgit") }; var category by remember { mutableStateOf("Makanan") }
   var target by remember { mutableStateOf("Bank Indonesia") }; var noteRate by remember { mutableStateOf(rate) }
   fun save(list:List<Tx>){ val a=JSONArray(); list.forEach { t->a.put(JSONObject().apply{put("date",t.date);put("name",t.name);put("kind",t.kind);put("category",t.category);put("account",t.account);put("currency",t.currency);put("amount",t.amount);put("rate",t.rate);put("target",t.target)}) }; prefs.edit().putString("txs",a.toString()).apply() }
   val r=rate.toDoubleOrNull()?.takeIf{it>0}?:3800.0
   val accounts=listOf("Cash Ringgit","E-wallet Malaysia","Bank Indonesia")
   fun balance(ac:String):Double {
    val opening=prefs.getString("open_$ac","0")?.toDoubleOrNull()?:0.0
    return opening+txs.sumOf { t -> when {
     t.kind=="Pemasukan" && t.account==ac -> t.amount
     t.kind=="Pengeluaran" && t.account==ac -> -t.amount
     t.kind=="Transfer" && t.account==ac -> -t.amount
     t.kind=="Transfer" && t.target==ac -> if(t.currency=="MYR" && ac=="Bank Indonesia") t.amount*t.rate else if(t.currency=="IDR" && ac!="Bank Indonesia") t.amount/t.rate else t.amount
     else -> 0.0
    }}
   }
   MaterialTheme(colorScheme=darkColorScheme(background=bg,surface=panel,primary=accent)) {
    Scaffold(containerColor=bg,bottomBar={NavigationBar(containerColor=panel){ listOf("Ringkasan","Transaksi","Laporan").forEach { label->NavigationBarItem(selected=tab==label,onClick={tab=label},icon={},label={Text(label)}) } } },
     floatingActionButton={ if(tab!="Laporan") FloatingActionButton(onClick={show=true},containerColor=accent,contentColor=bg){Text("+",style=MaterialTheme.typography.headlineMedium)} }) { pad->
     Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
      Text("MYMONEY",color=accent,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
      Text("Keuangan pribadi • Offline",color=Color.LightGray)
      Spacer(Modifier.height(16.dp))
      when(tab){
       "Ringkasan"->{
        Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(20.dp)){
         Column(Modifier.padding(18.dp)) {
          Text("TOTAL KEKAYAAN • NILAI IDR",color=Color.LightGray)
          val total=balance("Bank Indonesia")+balance("Cash Ringgit")*r+balance("E-wallet Malaysia")*r
          Text(money(total,"IDR"),style=MaterialTheme.typography.headlineMedium,color=Color.White,fontWeight=FontWeight.Bold)
          Spacer(Modifier.height(8.dp)); Text("Kurs pencatatan 1 MYR = Rp ${"%,.0f".format(Locale("id","ID"),r)}",color=accent)
         }
        }
        Spacer(Modifier.height(14.dp)); Text("SALDO PER TEMPAT",color=Color.LightGray,fontWeight=FontWeight.Bold)
        accounts.forEach { ac-> Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=panel)){Row(Modifier.padding(14.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(ac);Text(money(balance(ac),if(ac=="Bank Indonesia")"IDR" else "MYR",),color=accent,fontWeight=FontWeight.Bold)}}}
        Spacer(Modifier.height(14.dp)); Text("TRANSAKSI TERBARU",color=Color.LightGray,fontWeight=FontWeight.Bold)
        LazyColumn { items(txs.takeLast(5).reversed()){t->TxRow(t)} }
       }
       "Transaksi"->{
        Text("Semua transaksi",style=MaterialTheme.typography.titleLarge,color=Color.White)
        LazyColumn { items(txs.reversed()){t->TxRow(t)} }
       }
       else->{
        val month=LocalDate.now().toString().take(7)
        val inc=txs.filter{it.date.startsWith(month)&&it.kind=="Pemasukan"}.sumOf{it.amount*if(it.currency=="MYR")it.rate else 1.0}
        val exp=txs.filter{it.date.startsWith(month)&&it.kind=="Pengeluaran"}.sumOf{it.amount*if(it.currency=="MYR")it.rate else 1.0}
        Text("Laporan $month",style=MaterialTheme.typography.titleLarge,color=Color.White)
        Stat("Pemasukan",money(inc,"IDR")); Stat("Pengeluaran",money(exp,"IDR")); Stat("Selisih",money(inc-exp,"IDR"))
        Spacer(Modifier.height(12.dp)); Text("Catatan: transfer internal tidak dihitung sebagai pemasukan/pengeluaran.",color=Color.LightGray)
        OutlinedTextField(value=rate,onValueChange={rate=it; prefs.edit().putFloat("rate",it.toFloatOrNull()?:3800f).apply()},label={Text("Kurs MYR ke IDR")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
       }
      }
     }
    }
    if(show) MaterialTheme(colorScheme=darkColorScheme(background=bg,surface=panel,primary=accent)) { AlertDialog(onDismissRequest={show=false},containerColor=panel,title={Text("Tambah transaksi",color=Color.White)},text={
     Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Pemasukan","Pengeluaran","Transfer").forEach{FilterChip(selected=kind==it,onClick={kind=it},label={Text(it)})}}
      OutlinedTextField(name,{name=it},label={Text("Keterangan")},singleLine=true)
      OutlinedTextField(amount,{amount=it},label={Text("Nominal")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
      Row { listOf("MYR","IDR").forEach{FilterChip(selected=curr==it,onClick={curr=it},label={Text(it)})} }
      Text("Akun asal",color=Color.LightGray)
      accounts.forEach{ac->if((curr=="IDR")== (ac=="Bank Indonesia")) RadioRow(ac,account==ac){account=ac}}
      if(kind=="Transfer"){Text("Tujuan",color=Color.LightGray);accounts.filter{it!=account}.forEach{ac->RadioRow(ac,target==ac){target=ac}}}
      if(kind!="Transfer"){OutlinedTextField(category,{category=it},label={Text("Kategori")},singleLine=true)}
      Text("Tanggal: ${LocalDate.now()}",color=Color.LightGray)
      Text("Kurs disimpan pada transaksi: ${r.toInt()} IDR/MYR",color=accent)
     }
    },confirmButton={TextButton(onClick={
     val n=amount.replace(",","." ).toDoubleOrNull()
     if(name.isNotBlank()&&n!=null&&n>0){
      val t=Tx(LocalDate.now().toString(),name,kind,category,account,curr,n,r,if(kind=="Transfer")target else "")
      txs=txs+t;save(txs);show=false;name="";amount=""
     }
    }){Text("Simpan",color=accent)}},dismissButton={TextButton(onClick={show=false}){Text("Batal")}}) }
   }
  }
 }
}
@Composable private fun TxRow(t:Tx){Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=panel)){Row(Modifier.padding(12.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(t.name,color=Color.White,fontWeight=FontWeight.SemiBold);Text("${t.date} • ${t.account} • ${t.category}",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)};Text((if(t.kind=="Pengeluaran"||t.kind=="Transfer")"−" else "+")+money(t.amount,t.currency),color=if(t.kind=="Pemasukan")accent else Color.White)}}}
@Composable private fun Stat(label:String,value:String){Card(Modifier.fillMaxWidth().padding(vertical=5.dp),colors=CardDefaults.cardColors(containerColor=panel)){Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Color.LightGray);Text(value,color=Color.White,fontWeight=FontWeight.Bold)}}}
@Composable private fun RadioRow(label:String,selected:Boolean,onClick:()->Unit){Row {RadioButton(selected,onClick);Text(label,Modifier.padding(top=12.dp),color=Color.White)}}
private fun load(prefs:android.content.SharedPreferences):List<Tx>{return try{val a=JSONArray(prefs.getString("txs","[]"));(0 until a.length()).map{val o=a.getJSONObject(it);Tx(o.getString("date"),o.getString("name"),o.getString("kind"),o.getString("category"),o.getString("account"),o.getString("currency"),o.getDouble("amount"),o.getDouble("rate"),o.optString("target",""))}}catch(e:Exception){emptyList()}}
