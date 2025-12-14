package com.example.courseworkapp

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etLogin = findViewById<EditText>(R.id.etLogin)
        val etPass = findViewById<EditText>(R.id.etPass)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val cbShowPass = findViewById<CheckBox>(R.id.cbShowPass)

        cbShowPass.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                etPass.transformationMethod = HideReturnsTransformationMethod.getInstance()
            } else {
                etPass.transformationMethod = PasswordTransformationMethod.getInstance()
            }
        }

        btnLogin.setOnClickListener {
            val login = etLogin.text.toString()
            val pass = etPass.text.toString()

            if (login.isEmpty() || pass.isEmpty()) {
                tvStatus.text = "Please enter login and password"
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            tvStatus.text = "Connecting..."

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val role = DatabaseHelper.authenticate(login, pass)

                    btnLogin.isEnabled = true

                    when (role) {
                        UserRole.ADMIN -> {
                            tvStatus.text = "Success! Loading Admin..."
                            val intent = Intent(this@MainActivity, AdminActivity::class.java)
                            startActivity(intent)
                        }
                        UserRole.USER -> {
                            tvStatus.text = "Success! Loading User..."
                            val intent = Intent(this@MainActivity, UserActivity::class.java)
                            intent.putExtra("LOGIN", login)
                            startActivity(intent)
                        }
                        UserRole.NONE -> {
                            tvStatus.text = "Invalid login or password"
                        }
                    }
                } catch (e: Exception) {
                    btnLogin.isEnabled = true
                    tvStatus.text = "Error: ${e.message}"
                    e.printStackTrace()
                }
            }
        }
    }
}