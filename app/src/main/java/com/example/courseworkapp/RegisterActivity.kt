package com.example.courseworkapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etRegLogin = findViewById<EditText>(R.id.etRegLogin)
        val etRegPass = findViewById<EditText>(R.id.etRegPass)
        val btnDoRegister = findViewById<Button>(R.id.btnDoRegister)

        btnDoRegister.setOnClickListener {
            val l = etRegLogin.text.toString()
            val p = etRegPass.text.toString()

            if (l.isNotEmpty() && p.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val success = DatabaseHelper.registerUser(l, p)
                    if (success) {
                        Toast.makeText(this@RegisterActivity, "Registered!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@RegisterActivity, "Error registering", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}