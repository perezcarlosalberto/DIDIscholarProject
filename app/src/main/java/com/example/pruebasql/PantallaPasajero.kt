package com.example.pruebasql

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement

class PantallaPasajero : AppCompatActivity() {

    // --- Variables de Estado ---
    private var idPasajero: Int = -1
    private var idViajeActual: Int? = null
    private var costoViaje: Double = 0.0
    private var pollingJob: Job? = null

    // --- Conexión ---
    private val con = ConnectSql()

    // --- Vistas ---
    private lateinit var layoutFormulario: LinearLayout
    private lateinit var etCalle: EditText
    private lateinit var etNumero: EditText
    private lateinit var etColonia: EditText
    private lateinit var etCiudad: EditText
    private lateinit var etEstado: EditText
    private lateinit var etCP: EditText
    private lateinit var btnSolicitarViaje: Button

    private lateinit var layoutEspera: LinearLayout
    private lateinit var tvEstadoViaje: TextView
    private lateinit var pbEsperando: ProgressBar
    private lateinit var btnPagarViaje: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_pasajero)

        idPasajero = intent.getIntExtra("ID_USUARIO", -1)
        if (idPasajero == -1) {
            Toast.makeText(this, "Error: ID de Pasajero no encontrado", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Enlazar Vistas
        layoutFormulario = findViewById(R.id.layoutFormularioDestino)
        etCalle = findViewById(R.id.etDestinoCalle)
        etNumero = findViewById(R.id.etDestinoNumero)
        etColonia = findViewById(R.id.etDestinoColonia)
        etCiudad = findViewById(R.id.etDestinoCiudad)
        etEstado = findViewById(R.id.etDestinoEstado)
        etCP = findViewById(R.id.etDestinoCP)
        btnSolicitarViaje = findViewById(R.id.btnSolicitarViaje)

        layoutEspera = findViewById(R.id.layoutEsperaPago)
        tvEstadoViaje = findViewById(R.id.tvEstadoViaje)
        pbEsperando = findViewById(R.id.pbEsperando)
        btnPagarViaje = findViewById(R.id.btnPagarViaje)

        btnSolicitarViaje.setOnClickListener {
            solicitarViaje()
        }

        btnPagarViaje.setOnClickListener {
            if (idViajeActual != null) {
                val intent = Intent(this, PantallaPago::class.java).apply {
                    putExtra("ID_VIAJE", idViajeActual)
                    putExtra("ID_PASAJERO", idPasajero)
                    putExtra("MONTO_VIAJE", costoViaje)
                }
                startActivity(intent)
            }
        }
    }

    private fun solicitarViaje() {
        val calle = etCalle.text.toString().trim()
        var numero = etNumero.text.toString().trim()
        val colonia = etColonia.text.toString().trim()
        val ciudad = etCiudad.text.toString().trim()
        val estado = etEstado.text.toString().trim()
        val cp = etCP.text.toString().trim()

        // --- VALIDACIONES ---
        if (calle.isEmpty() || colonia.isEmpty() || ciudad.isEmpty() || estado.isEmpty() || cp.isEmpty()) {
            Toast.makeText(this, "Por favor llena todos los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        if (cp.length != 5 || !cp.all { it.isDigit() }) {
            Toast.makeText(this, "El Código Postal debe ser de 5 números", Toast.LENGTH_SHORT).show()
            return
        }

        // --- CORRECCIÓN PRINCIPAL PARA EL ERROR DE SQL ---
        // Si el número está vacío, lo convertimos a "0" para que SQL no falle.
        if (numero.isEmpty()) {
            numero = "0"
        } else if (!numero.all { it.isDigit() }) {
            Toast.makeText(this, "El número de casa solo acepta dígitos", Toast.LENGTH_LONG).show()
            return
        }

        btnSolicitarViaje.isEnabled = false
        btnSolicitarViaje.text = "Procesando..."

        lifecycleScope.launch {
            costoViaje = 50.0 + (Math.random() * 100) // Costo simulado

            val (idDireccion, idViaje, errorMsg) = crearDireccionYViajeEnBD(calle, numero, colonia, ciudad, estado, cp)

            if (idViaje != null) {
                idViajeActual = idViaje
                Toast.makeText(this@PantallaPasajero, "Viaje Solicitado (ID: $idViaje)", Toast.LENGTH_SHORT).show()
                mostrarVistaEspera()
                iniciarMonitoreoDeViaje(idViaje)
            } else {
                Toast.makeText(this@PantallaPasajero, "Error SQL: $errorMsg", Toast.LENGTH_LONG).show()
                btnSolicitarViaje.isEnabled = true
                btnSolicitarViaje.text = "Solicitar Viaje"
            }
        }
    }

    private suspend fun crearDireccionYViajeEnBD(
        calle: String, numero: String, col: String, cd: String, edo: String, cp: String
    ): Triple<Int?, Int?, String> {
        return withContext(Dispatchers.IO) {
            var conn: Connection? = null
            var idDir: Int? = null
            var idViaje: Int? = null
            var msgError = ""

            try {
                conn = con.dbConn()
                if (conn == null) throw SQLException("No hay conexión a la BD")

                conn.autoCommit = false

                // 1. Insertar Dirección
                val sqlDir = "INSERT INTO DIRECCION (CALLE, NUMERO_CASA, COLONIA, CIUDAD, ESTADO, PAIS, CODIGO_POSTAL) VALUES (?, ?, ?, ?, ?, ?, ?)"
                val psDir = conn.prepareStatement(sqlDir, Statement.RETURN_GENERATED_KEYS)
                psDir.setString(1, calle)
                psDir.setString(2, numero) // Ahora seguro es "0" o dígitos
                psDir.setString(3, col)
                psDir.setString(4, cd)
                psDir.setString(5, edo)
                psDir.setString(6, "Mexico")
                psDir.setString(7, cp)

                psDir.executeUpdate()
                val rsDir = psDir.generatedKeys
                if (rsDir.next()) idDir = rsDir.getInt(1)

                if (idDir == null) throw SQLException("Fallo al crear la dirección")

                // 2. Insertar Viaje (Usando CURRENT_TIMESTAMP)
                val sqlViaje = """
                    INSERT INTO VIAJE (HORA_INICIO, HORA_FIN, FECHA_VIAJE, COSTO, ID_PASAJERO, ID_DIRECCION) 
                    VALUES (CONVERT(TIME, GETDATE()), CONVERT(TIME, GETDATE()), GETDATE(), ?, ?, ?)
                """
                val psViaje = conn.prepareStatement(sqlViaje, Statement.RETURN_GENERATED_KEYS)
                psViaje.setDouble(1, costoViaje)
                psViaje.setInt(2, idPasajero)
                psViaje.setInt(3, idDir!!)

                psViaje.executeUpdate()
                val rsViaje = psViaje.generatedKeys
                if (rsViaje.next()) idViaje = rsViaje.getInt(1)

                conn.commit()

            } catch (e: Exception) {
                e.printStackTrace()
                msgError = e.message ?: "Error desconocido"
                try { conn?.rollback() } catch (ex: SQLException) { ex.printStackTrace() }
            } finally {
                try { conn?.close() } catch (e: Exception) {}
            }

            Triple(idDir, idViaje, msgError)
        }
    }

    private fun mostrarVistaEspera() {
        layoutFormulario.visibility = View.GONE
        layoutEspera.visibility = View.VISIBLE
        tvEstadoViaje.text = "Buscando conductor cercano..."
        pbEsperando.visibility = View.VISIBLE
        btnPagarViaje.visibility = View.GONE
    }

    private fun iniciarMonitoreoDeViaje(idViaje: Int) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                val idConductor = verificarEstadoViaje(idViaje)

                if (idConductor != null && idConductor > 0) {
                    tvEstadoViaje.text = "¡Conductor asignado! (ID: $idConductor)\nProcede al pago."
                    tvEstadoViaje.setTextColor(Color.parseColor("#4CAF50")) // Verde
                    pbEsperando.visibility = View.GONE
                    btnPagarViaje.visibility = View.VISIBLE
                    cancel()
                }
                delay(4000)
            }
        }
    }

    private suspend fun verificarEstadoViaje(idViaje: Int): Int? {
        return withContext(Dispatchers.IO) {
            var idCond: Int? = null
            val conn = con.dbConn()
            try {
                if (conn != null) {
                    val sql = "SELECT ID_CONDUCTOR FROM VIAJE WHERE ID_VIAJE = ?"
                    val ps = conn.prepareStatement(sql)
                    ps.setInt(1, idViaje)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        val conductor = rs.getInt("ID_CONDUCTOR")
                        if (!rs.wasNull() && conductor > 0) {
                            idCond = conductor
                        }
                    }
                    conn.close()
                }
            } catch (e: Exception) { e.printStackTrace() }
            idCond
        }
    }

    override fun onStop() {
        super.onStop()
        pollingJob?.cancel()
    }
}