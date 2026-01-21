package com.example.pruebasql // <-- CORREGIDO A MINÚSCULA

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView

class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        // --- CORREGIDO A MINÚSCULA ---
        val nombreUsuario = intent.getStringExtra("com.example.pruebasql.USUARIO")

        val btnsalir=findViewById<Button>(R.id.btnsalir)
        val continuar=findViewById<Button>(R.id.BTN2)
        val tv1=findViewById<TextView>(R.id.tv1)
        val spinner=findViewById<Spinner>(R.id.spinner)
        val lista = arrayOf("Consultar", "Insertar", "Eliminar", "Actualizar")
        val adaptador1 = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, lista)
        spinner.adapter = adaptador1

        if (nombreUsuario != null) {
            tv1.text = tv1.text.toString() + " " + nombreUsuario
        }

        continuar.setOnClickListener {
            when (spinner.selectedItem.toString()) {
                "Consultar" -> tv1.text = "Operación en proceso"
                "Insertar" -> {
                    val intent = Intent(this, Insertar_usuario::class.java)
                    startActivity(intent)
                }
                "Eliminar" -> tv1.text = "Operación en proceso"
                "Actualizar" -> tv1.text = "Operación en proceso"
            }
        }

        btnsalir.setOnClickListener {
            finish()
        }
    }
}