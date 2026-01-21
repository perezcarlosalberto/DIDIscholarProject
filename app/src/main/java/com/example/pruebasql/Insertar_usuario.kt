package com.example.pruebasql

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.Locale

class Insertar_usuario : AppCompatActivity() {

    private lateinit var etNombre: EditText
    private lateinit var etApellido: EditText
    private lateinit var etCorreo: EditText
    private lateinit var etTelefono: EditText
    private lateinit var etEdad: EditText
    private lateinit var etPass: EditText
    private lateinit var etConfirmPass: EditText
    private lateinit var spinnerTipoUsuario: Spinner
    private lateinit var btnRegistrar: Button
    private lateinit var btnRegresar: Button

    private val con = ConnectSql()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insertar)

        etNombre = findViewById(R.id.etNombre)
        etApellido = findViewById(R.id.etApellido)
        etCorreo = findViewById(R.id.etCorreo)
        etTelefono = findViewById(R.id.etTelefono)
        etEdad = findViewById(R.id.etEdad)
        etPass = findViewById(R.id.etPassword)
        etConfirmPass = findViewById(R.id.etConfirmPassword)
        spinnerTipoUsuario = findViewById(R.id.spinnerTipoUsuario)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        btnRegresar = findViewById(R.id.btnregresarregistrar)

        val tiposUsuario = arrayOf("PASAJERO", "CONDUCTOR", "PERSONAL OPERATIVO")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tiposUsuario)
        spinnerTipoUsuario.adapter = spinnerAdapter

        btnRegresar.setOnClickListener { finish() }
        btnRegistrar.setOnClickListener { intentarRegistro() }
    }

    // --- FUNCIÓN AUXILIAR PARA FORMATEAR NOMBRES ---
    // Convierte "jUaN cArLos" -> "Juan Carlos"
    private fun formatearNombre(texto: String): String {
        if (texto.isEmpty()) return ""
        // 1. Convertir todo a minúsculas
        // 2. Separar por espacios
        // 3. Poner mayúscula a la primera letra de cada palabra
        return texto.lowercase(Locale.getDefault())
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }

    private fun intentarRegistro() {
        val nombreRaw = etNombre.text.toString().trim()
        val apellidoRaw = etApellido.text.toString().trim()
        val correo = etCorreo.text.toString().trim()
        val telefono = etTelefono.text.toString().trim()
        val edadStr = etEdad.text.toString().trim()
        val pass = etPass.text.toString().trim()
        val confirmPass = etConfirmPass.text.toString().trim()

        val tipoUsuarioItem = spinnerTipoUsuario.selectedItem
        if (tipoUsuarioItem == null) return
        val tipoUsuario = tipoUsuarioItem.toString()

        // --- VALIDACIONES ---

        if (nombreRaw.isEmpty() || apellidoRaw.isEmpty() || correo.isEmpty() || telefono.isEmpty() || edadStr.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        // Aplicar formato bonito a Nombres y Apellidos aquí
        val nombreFormateado = formatearNombre(nombreRaw)
        val apellidoFormateado = formatearNombre(apellidoRaw)

        // 1. Validar Nombre/Apellido (Solo letras y espacios)
        val nombreRegex = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+$")
        if (!nombreRaw.matches(nombreRegex) || !apellidoRaw.matches(nombreRegex)) {
            Toast.makeText(this, "Nombres y apellidos no pueden contener números", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Validar Correo (Dominios permitidos)
        val dominiosValidos = listOf("@gmail.com", "@hotmail.com", "@cdmadero.tecnm.mx")
        if (dominiosValidos.none { correo.endsWith(it) }) {
            Toast.makeText(this, "Correo inválido. Solo se aceptan Gmail, Hotmail o Tec Madero", Toast.LENGTH_LONG).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            Toast.makeText(this, "Formato de correo inválido", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. Validar Teléfono
        if (telefono.length != 10 || !telefono.all { it.isDigit() }) {
            Toast.makeText(this, "El teléfono debe tener 10 dígitos", Toast.LENGTH_LONG).show()
            return
        }

        // 4. Validar Edad
        val edad = edadStr.toIntOrNull()
        if (edad == null || edad < 18) {
            Toast.makeText(this, "Debes ser mayor de 18 años", Toast.LENGTH_SHORT).show()
            return
        }

        // 5. Validar Contraseña
        if (pass.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        if (pass != confirmPass) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            return
        }

        // --- INSERCIÓN (Usando los nombres formateados) ---
        lifecycleScope.launch {
            btnRegistrar.isEnabled = false
            btnRegistrar.text = "Registrando..."

            // Enviamos nombreFormateado y apellidoFormateado
            val resultado = insertarEnBD(nombreFormateado, apellidoFormateado, correo, edad, telefono, tipoUsuario, pass)

            when (resultado) {
                "EXITO" -> {
                    Toast.makeText(this@Insertar_usuario, "¡Registro Exitoso! Inicia sesión.", Toast.LENGTH_LONG).show()
                    finish()
                }
                "DUPLICADO" -> {
                    Toast.makeText(this@Insertar_usuario, "Ese correo ya existe, por favor ingrese otro.", Toast.LENGTH_LONG).show()
                    btnRegistrar.isEnabled = true
                    btnRegistrar.text = "REGISTRARME"
                }
                else -> {
                    Toast.makeText(this@Insertar_usuario, "Error: $resultado", Toast.LENGTH_LONG).show()
                    btnRegistrar.isEnabled = true
                    btnRegistrar.text = "REGISTRARME"
                }
            }
        }
    }

    private suspend fun insertarEnBD(
        nombre: String, apellido: String, correo: String, edad: Int,
        telefono: String, tipo: String, pass: String
    ): String {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = con.dbConn() ?: return@withContext "Error de Conexión"
                connection.autoCommit = false

                // Insertar Usuario (con CONTRASEÑA y Nombres Formateados)
                val sqlUsuario = "INSERT INTO USUARIO (NOMBRE_USUARIO, APELLIDO_USUARIO, CORREO, EDAD_USUARIO, TELEFONO, TIPO_USUARIO, CONTRASENA) VALUES (?, ?, ?, ?, ?, ?, ?)"
                val ps = connection.prepareStatement(sqlUsuario, Statement.RETURN_GENERATED_KEYS)
                ps.setString(1, nombre)
                ps.setString(2, apellido)
                ps.setString(3, correo)
                ps.setInt(4, edad)
                ps.setString(5, telefono)
                ps.setString(6, tipo)
                ps.setString(7, pass)

                ps.executeUpdate()

                val rs = ps.generatedKeys
                var idGenerado = -1
                if (rs.next()) idGenerado = rs.getInt(1)

                // Insertar Rol
                val sqlHija = "INSERT INTO ${tipo.replace(" ", "_")} (ID_${tipo.replace(" ", "_")}) VALUES (?)"
                val psHija = connection.prepareStatement(sqlHija)
                psHija.setInt(1, idGenerado)
                psHija.executeUpdate()

                connection.commit()
                return@withContext "EXITO"

            } catch (e: SQLException) {
                connection?.rollback()
                if (e.errorCode == 2627 || e.errorCode == 2601) {
                    return@withContext "DUPLICADO"
                }
                e.printStackTrace()
                return@withContext e.message ?: "Error SQL"
            } catch (e: Exception) {
                return@withContext e.message ?: "Error Genérico"
            } finally {
                connection?.close()
            }
        }
    }
}