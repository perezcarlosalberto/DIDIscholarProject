package com.example.pruebasql

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException

class MainActivity : AppCompatActivity() {

    private val con = ConnectSql()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        setContentView(R.layout.activity_main)

        val etCorreo = findViewById<EditText>(R.id.etCorreoLogin)
        val etPassword = findViewById<EditText>(R.id.etPasswordLogin)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegistrar = findViewById<Button>(R.id.btnRegistrar)

        btnLogin.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
            } else {
                validarCredenciales(correo, password)
            }
        }

        btnRegistrar.setOnClickListener {
            val intento = Intent(this, Insertar_usuario::class.java)
            startActivity(intento)
        }
    }

    private fun validarCredenciales(correo: String, pass: String) {
        lifecycleScope.launch {
            val btn = findViewById<Button>(R.id.btnLogin)
            btn.isEnabled = false
            btn.text = "Verificando..."

            val usuario = buscarUsuarioEnBD(correo, pass)

            if (usuario != null) {
                val (id, tipo, nombre) = usuario
                Toast.makeText(this@MainActivity, "Bienvenido $nombre", Toast.LENGTH_SHORT).show()

                val intent = when (tipo) {
                    "PERSONAL OPERATIVO" -> Intent(this@MainActivity, PantallaOperativo::class.java)
                    "CONDUCTOR" -> Intent(this@MainActivity, PantallaConductor::class.java)
                    "PASAJERO" -> Intent(this@MainActivity, PantallaPasajero::class.java)
                    else -> null
                }

                if (intent != null) {
                    intent.putExtra("ID_USUARIO", id)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this@MainActivity, "Credenciales incorrectas", Toast.LENGTH_LONG).show()
            }

            btn.isEnabled = true
            btn.text = "INICIAR SESIÓN"
        }
    }

    private suspend fun buscarUsuarioEnBD(correo: String, pass: String): Triple<Int, String, String>? {
        return withContext(Dispatchers.IO) {
            var resultado: Triple<Int, String, String>? = null
            val connection = con.dbConn() ?: return@withContext null

            try {
                val query = "SELECT ID_USUARIO, TIPO_USUARIO, NOMBRE_USUARIO FROM USUARIO WHERE CORREO = ? AND CONTRASENA = ?"
                val ps = connection.prepareStatement(query)
                ps.setString(1, correo)
                ps.setString(2, pass)

                val rs = ps.executeQuery()
                if (rs.next()) {
                    val id = rs.getInt("ID_USUARIO")
                    val tipo = rs.getString("TIPO_USUARIO")
                    val nombre = rs.getString("NOMBRE_USUARIO")
                    resultado = Triple(id, tipo, nombre)
                }
                connection.close()
            } catch (e: SQLException) { e.printStackTrace() }
            resultado
        }
    }
}