package com.blusher.cosmetics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Product(val id:Int,val name:String,val category:String,val price:Int,val description:String)

val products = listOf(
 Product(1,"بلشر ناعم","بلشر",12000,"لمسة ناعمة وطبيعية للاستخدام اليومي."),
 Product(2,"روج مخملي","روج",10000,"لون غني بلمسة مخملية أنيقة."),
 Product(3,"ماسكارا فوليوم","عيون",15000,"كثافة واضحة وثبات مناسب."),
 Product(4,"كونتور ستيك","وجه",14000,"تحديد سهل ونتيجة طبيعية."),
 Product(5,"هايلايتر جلو","وجه",13000,"إضاءة ناعمة ولمعة أنيقة."),
 Product(6,"ليب غلوس","شفاه",9000,"لمعة خفيفة ومظهر جذاب.")
)
val categories = listOf("الكل","بلشر","روج","عيون","وجه","شفاه")

class MainActivity: ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{BlusherApp()}}
}

@Composable
fun BlusherApp(){
 var category by remember{mutableStateOf("الكل")}
 var search by remember{mutableStateOf("")}
 var cart by remember{mutableStateOf<Map<Int,Int>>(emptyMap())}
 var detail by remember{mutableStateOf<Product?>(null)}
 var showCart by remember{mutableStateOf(false)}
 val list=products.filter{(category=="الكل"||it.category==category)&&it.name.contains(search.trim(),true)}
 val count=cart.values.sum()
 val total=cart.entries.sumOf{e->products.first{it.id==e.key}.price*e.value}
 MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFFB84D70),secondary=Color(0xFFE9A5B8),background=Color(0xFFFFF8FA),surface=Color.White)){
  Surface(Modifier.fillMaxSize(),Color(0xFFFFF8FA)){
   when{
    detail!=null->Details(detail!!,cart[detail!!.id]?:0,{detail=null},{val id=detail!!.id;cart=cart+(id to((cart[id]?:0)+1))},{val id=detail!!.id;val q=(cart[id]?:0)-1;cart=if(q<=0)cart-id else cart+(id to q)})
    showCart->Cart(cart,total,{showCart=false},{cart=emptyMap()},{id->val q=(cart[id]?:0)-1;cart=if(q<=0)cart-id else cart+(id to q)})
    else->Home(search,{search=it},category,{category=it},list,count,{showCart=true},{detail=it},{p->cart=cart+(p.id to((cart[p.id]?:0)+1))})
   }
  }
 }
}

@Composable
fun Home(search:String,onSearch:(String)->Unit,category:String,onCategory:(String)->Unit,list:List<Product>,count:Int,onCart:()->Unit,onProduct:(Product)->Unit,onAdd:(Product)->Unit){
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){Text("بلشر كوزمتك",27.sp, fontWeight=FontWeight.Bold,color=Color(0xFF9F3E60));Text("كل الجمال بمكان واحد",color=Color.Gray)}
   BadgedBox({if(count>0)Badge{Text(count.toString())}}){IconButton(onCart){Icon(Icons.Default.ShoppingBag,"السلة",tint=Color(0xFF9F3E60))}}
  }}
  item{OutlinedTextField(search,onSearch,Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(18.dp),placeholder={Text("ابحثي عن منتج...")},leadingIcon={Icon(Icons.Default.Search,null)})}
  item{Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(Color(0xFFF6DCE5))){Column(Modifier.padding(20.dp)){Text("تشكيلة جديدة ✨",23.sp,fontWeight=FontWeight.Bold);Text("اختاري منتجاتك المفضلة وخلي إطلالتك أحلى.");Spacer(Modifier.height(10.dp));Button({onCategory("بلشر")}){Text("تسوقي الآن")}}}}
  item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(categories){c->FilterChip(category==c,{onCategory(c)},{Text(c)})}}}
  item{Text("المنتجات",21.sp,fontWeight=FontWeight.Bold)}
  items(list){p->ProductCard(p,onProduct,onAdd)}
  if(list.isEmpty())item{Text("ماكو منتجات مطابقة للبحث.",Modifier.fillMaxWidth(),textAlign=TextAlign.Center)}
 }
}

@Composable
fun ProductCard(p:Product,onProduct:(Product)->Unit,onAdd:(Product)->Unit){
 Card(Modifier.fillMaxWidth().clickable{onProduct(p)},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(Color.White)){
  Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(78.dp).background(Color(0xFFF4D5DF),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Face,null,tint=Color(0xFFB84D70),modifier=Modifier.size(36.dp))}
   Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(p.category,color=Color.Gray);Text(p.price.toString()+" د.ع",color=Color(0xFFB84D70),fontWeight=FontWeight.Bold)}
   IconButton({onAdd(p)}){Icon(Icons.Default.AddShoppingCart,"إضافة للسلة",tint=Color(0xFFB84D70))}
  }
 }
}

@Composable
fun Details(p:Product,q:Int,back:()->Unit,add:()->Unit,remove:()->Unit){
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){IconButton(back){Icon(Icons.Default.ArrowBack,"رجوع")};Text(p.name,22.sp,fontWeight=FontWeight.Bold)}
  Spacer(Modifier.height(18.dp))
  Box(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFF4D5DF),RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Face,null,tint=Color(0xFFB84D70),modifier=Modifier.size(100.dp))}
  Spacer(Modifier.height(18.dp));Text(p.name,25.sp,fontWeight=FontWeight.Bold);Text(p.category,color=Color.Gray);Spacer(Modifier.height(8.dp));Text(p.description,fontSize=17.sp);Spacer(Modifier.height(10.dp));Text(p.price.toString()+" د.ع",22.sp,fontWeight=FontWeight.Bold,color=Color(0xFFB84D70));Spacer(Modifier.weight(1f))
  if(q>0)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){OutlinedButton(remove){Text("−")};Text("  "+q+"  ",20.sp,fontWeight=FontWeight.Bold);Button(add){Text("+")}}
  else Button(add,Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("إضافة إلى السلة",17.sp)}
 }
}

@Composable
fun Cart(cart:Map<Int,Int>,total:Int,back:()->Unit,clear:()->Unit,remove:(Int)->Unit){
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){IconButton(back){Icon(Icons.Default.ArrowBack,"رجوع")};Text("سلة المشتريات",24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(cart.isNotEmpty())TextButton(clear){Text("مسح")}}
  if(cart.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("السلة فارغة 🛍️",20.sp)}
  else{
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){items(cart.entries.toList()){e->val p=products.first{it.id==e.key};Card(Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text(p.price.toString()+" د.ع × "+e.value)};IconButton({remove(p.id)}){Icon(Icons.Default.Remove,"إنقاص")}}}}}
   HorizontalDivider();Spacer(Modifier.height(10.dp));Text("المجموع: "+total+" د.ع",21.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Button({},Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("إتمام الطلب")}
  }
 }
}
