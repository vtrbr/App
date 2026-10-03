package com.tedflix.app.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.tedflix.app.MainActivity
import com.tedflix.app.R
import androidx.media3.common.util.UnstableApi

@UnstableApi
class AuthActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var message: TextView
    private var busy=false
    override fun onCreate(state:Bundle?){super.onCreate(state);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;AuthSession.init(applicationContext);showLogin(null)}
    private fun showLogin(error:String?){
        root=backdrop();content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(25),dp(30),dp(25),dp(28))}
        content.addView(ImageView(this).apply{setImageResource(R.drawable.tedflix_auth_logo);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(-1,dp(78)).apply{bottomMargin=dp(14)})
        content.addView(title("Entrar no Tedflix")); content.addView(subtitle("Use o e-mail e a senha da sua conta"))
        val email=field("E-mail","seu@email.com",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val pass=field("Senha","Sua senha",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(email,params());content.addView(pass,params())
        val enter=button("ENTRAR",true){login(email.text.toString(),pass.text.toString())};content.addView(enter,buttonParams())
        content.addView(button("Esqueci minha senha",false){showForgot() },buttonParams())
        content.addView(button("Criar uma conta",false){showRegister(null)},buttonParams())
        finishLayout(error)
    }
    private fun showRegister(error:String?){
        root=backdrop();content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(25),dp(30),dp(25),dp(28))}
        content.addView(ImageView(this).apply{setImageResource(R.drawable.tedflix_auth_logo);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(-1,dp(70)).apply{bottomMargin=dp(10)})
        content.addView(title("Criar sua conta"));content.addView(subtitle("Sem código de acesso: o cadastro é seu"))
        val name=field("Nome","Como devemos chamar você?",InputType.TYPE_CLASS_TEXT);val email=field("E-mail","seu@email.com",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);val pass=field("Senha","Crie uma senha forte",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD);val confirm=field("Confirmar senha","Repita a senha",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        listOf(name,email,pass,confirm).forEach{content.addView(it,params())}
        content.addView(button("CRIAR CONTA",true){register(name.text.toString(),email.text.toString(),pass.text.toString(),confirm.text.toString())},buttonParams())
        content.addView(button("Já tenho uma conta",false){showLogin(null)},buttonParams());finishLayout(error)
    }
    private fun showForgot(){root=backdrop();content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(25),dp(40),dp(25),dp(28))};content.addView(title("Recuperar senha"));content.addView(subtitle("Se o e-mail existir, enviaremos as instruções"));val email=field("E-mail","seu@email.com",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);content.addView(email,params());content.addView(button("ENVIAR INSTRUÇÕES",true){Thread{val r=AuthSession.forgotPassword(email.text.toString());runOnUiThread{showLogin(r.message.ifBlank{"Confira seu e-mail para continuar."})}}.start()},buttonParams());content.addView(button("Voltar",false){showLogin(null)},buttonParams());finishLayout(null)}
    private fun login(e:String,p:String){if(e.isBlank()||p.isBlank()){showLogin("Informe e-mail e senha.");return};busy=true;Thread{val r=AuthSession.login(e.trim(),p);runOnUiThread{busy=false;if(r.ok)openMain()else showLogin(r.message)}}.start()}
    private fun register(n:String,e:String,p:String,c:String){when{n.isBlank()->showRegister("Informe seu nome.");e.isBlank()->showRegister("Informe seu e-mail.");p.length<6->showRegister("A senha deve ter pelo menos 6 caracteres.");p!=c->showRegister("As senhas não conferem.");else->{busy=true;Thread{val r=AuthSession.register(n.trim(),e.trim(),p);runOnUiThread{busy=false;if(r.ok)openMain()else showRegister(r.message)}}.start()}}}
    private fun openMain(){startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK));finish()}
    private fun finishLayout(error:String?){root.addView(ScrollView(this).apply{isFillViewport=true;addView(content)},FrameLayout.LayoutParams(-1,-1));setContentView(root);if(!error.isNullOrBlank())showError(error)}
    private fun title(t:String)=TextView(this).apply{text=t;textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setPadding(0,0,0,dp(7))}
    private fun subtitle(t:String)=TextView(this).apply{text=t;textSize=15f;setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(0,0,0,dp(22))}
    private fun field(label:String,hintText:String,type:Int)=EditText(this).apply{hint=hintText;contentDescription=label;inputType=type;setSingleLine(true);textSize=16f;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setPadding(dp(14),0,dp(14),0);background=rounded(Color.rgb(15,15,15),dp(9),Color.DKGRAY,dp(1))}
    private fun button(textValue:String,primary:Boolean,onClick:()->Unit)=Button(this).apply{text=textValue;isAllCaps=false;textSize=16f;setTextColor(Color.WHITE);background=rounded(if(primary) Color.rgb(229,9,20) else Color.rgb(38,38,42),dp(10),Color.TRANSPARENT,0);setOnClickListener{if(!busy)onClick()}}
    private fun showError(s:String){if(!::message.isInitialized){message=TextView(this);content.addView(message)};message.text=s;message.setTextColor(Color.rgb(255,120,120));message.gravity=Gravity.CENTER;message.setPadding(dp(10),dp(12),dp(10),dp(12))}
    private fun params()=LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(12)};private fun buttonParams()=LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(5)}
    private fun backdrop()=FrameLayout(this).apply{setBackgroundColor(Color.BLACK);addView(ImageView(this@AuthActivity).apply{setImageResource(R.drawable.tedflix_auth_banner);scaleType=ImageView.ScaleType.CENTER_CROP;alpha=.5f},FrameLayout.LayoutParams(-1,-1));addView(View(this@AuthActivity).apply{setBackgroundColor(Color.argb(175,0,0,0))},FrameLayout.LayoutParams(-1,-1))}
    private fun rounded(fill:Int,radius:Int,stroke:Int,width:Int)=android.graphics.drawable.GradientDrawable().apply{setColor(fill);cornerRadius=radius.toFloat();if(width>0)setStroke(width,stroke)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
