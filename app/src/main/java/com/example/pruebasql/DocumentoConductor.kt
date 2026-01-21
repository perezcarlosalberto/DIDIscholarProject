package com.example.pruebasql

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.pruebasql.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.text.SimpleDateFormat

class DocumentoConductor : AppCompatActivity() {

    private lateinit var spinnerTipoDocumento: Spinner
    private lateinit var etNumeroPoliza: EditText
    private lateinit var etFechaEmision: EditText
    private lateinit var etFechaVencimiento: EditText
    private lateinit var spinnerEstado: Spinner
    private lateinit var btnRegistrarDocumento: Button

    private val con = ConnectSql()
    private var idConductor: Int = -1

    private val sdf = SimpleDateFormat("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documento_conductor)

        idConductor = intent.getIntExtra("ID_USUARIO", -1)
        if (idConductor == -1) {
            Toast.makeText(this, "Error crítico: No se recibió el ID del conductor", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        spinnerTipoDocumento = findViewById(R.id.spinnerTipoDocumento)
        etNumeroPoliza = findViewById(R.id.etNumeroPoliza)
        etFechaEmision = findViewById(R.id.etFechaEmision)
        etFechaVencimiento = findViewById(R.id.etFechaVencimiento)
        spinnerEstado = findViewById(R.id.spinnerEstado)
        btnRegistrarDocumento = findViewById(R.id.btnRegistrarDocumento)

        // Configurar Spinners (con el layout personalizado)
        val tiposDocumento = arrayOf("POLIZA DE SEGURO DE VIDA", "POLIZA DE SEGURO MEDICO", "POLIZA DE AUTOMOVIL")
        val adapterTipoDoc = ArrayAdapter(this, R.layout.spinner_item_personalizado, tiposDocumento)
        adapterTipoDoc.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTipoDocumento.adapter = adapterTipoDoc

        val estados = arrayOf("VIGENTE", "VENCIDA")
        val adapterEstado = ArrayAdapter(this, R.layout.spinner_item_personalizado, estados)
        adapterEstado.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEstado.adapter = adapterEstado

        btnRegistrarDocumento.setOnClickListener {
            intentarRegistroDocumento()
            // --- CORRECCIÓN DE BUG: Se quitó irAlDashboardFinal() de aquí ---
        }
    }

    private fun esFechaValida(fecha: String): Boolean {
        val regexFecha = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (!fecha.matches(regexFecha)) {
            return false
        }
        return try {
            sdf.isLenient = false
            sdf.parse(fecha)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun intentarRegistroDocumento() {
        try {
            val tipoDocumento = spinnerTipoDocumento.selectedItem.toString()
            val estado = spinnerEstado.selectedItem.toString()

            val numPolizaStr = etNumeroPoliza.text.toString().trim()
            val fechaEmision = etFechaEmision.text.toString().trim()
            val fechaVencimiento = etFechaVencimiento.text.toString().trim()

            // --- VALIDACIONES ESTRICTAS ---
            if (numPolizaStr.isEmpty() || fechaEmision.isEmpty() || fechaVencimiento.isEmpty()) {
                Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
                return
            }
            val numPoliza = numPolizaStr.toIntOrNull()
            if (numPoliza == null || numPoliza <= 0) {
                Toast.makeText(this, "El número de póliza debe ser un número válido y mayor a 0", Toast.LENGTH_SHORT).show()
                return
            }
            if (!esFechaValida(fechaEmision)) {
                Toast.makeText(this, "La 'Fecha de Emisión' no es una fecha válida (use YYYY-MM-DD)", Toast.LENGTH_LONG).show()
                return
            }
            if (!esFechaValida(fechaVencimiento)) {
                Toast.makeText(this, "La 'Fecha de Vencimiento' no es una fecha válida (use YYYY-MM-DD)", Toast.LENGTH_LONG).show()
                return
            }
            val fechaEmiParsed = sdf.parse(fechaEmision)
            val fechaVenParsed = sdf.parse(fechaVencimiento)
            if (fechaVenParsed.before(fechaEmiParsed) || fechaVenParsed == fechaEmiParsed) {
                Toast.makeText(this, "La fecha de vencimiento debe ser posterior a la fecha de emisión", Toast.LENGTH_LONG).show()
                return
            }
            // --- FIN DE VALIDACIONES ---

            lifecycleScope.launch {
                try {
                    btnRegistrarDocumento.isEnabled = false
                    btnRegistrarDocumento.text = "Registrando..."

                    val exito = insertarDocumentoEnBD(tipoDocumento, numPoliza, fechaEmision, fechaVencimiento, estado, idConductor)

                    if (exito) {
                        Toast.makeText(this@DocumentoConductor, "¡Documento registrado!", Toast.LENGTH_SHORT).show()

                        // --- CORRECCIÓN DE BUG: Se navega SÓLO si hay éxito ---
                        irAlDashboardFinal()

                    } else {
                        Toast.makeText(this@DocumentoConductor, "Error al registrar. Revisa los datos (póliza duplicada, etc.)", Toast.LENGTH_LONG).show()
                        btnRegistrarDocumento.isEnabled = true
                        btnRegistrarDocumento.text = "Registrar Documento"
                    }

                } catch (t: Throwable) {
                    t.printStackTrace()
                    Toast.makeText(this@DocumentoConductor, "ERROR DE RED/SQL: ${t.message}", Toast.LENGTH_LONG).show()
                    btnRegistrarDocumento.isEnabled = true
                    btnRegistrarDocumento.text = "Registrar Documento"
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "ERROR DE APP: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun irAlDashboardFinal() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("ID_USUARIO", idConductor)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private suspend fun insertarDocumentoEnBD(
        tipoDoc: String,
        numPoliza: Int,
        fechaEmi: String,
        fechaVen: String,
        estado: String,
        conductorId: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            var exito = false

            val sql = "INSERT INTO DOCUMENTO (TIPO_DOCUMENTO, NUMERO_POLIZA, FECHA_EMISION, FECHA_VENCIMIENTO, ESTADO, ID_CONDUCTOR) VALUES (?, ?, ?, ?, ?, ?)"

            try {
                connection = con.dbConn()
                if (connection == null) {
                    return@withContext false
                }

                statement = connection.prepareStatement(sql)
                statement.setString(1, tipoDoc)
                statement.setInt(2, numPoliza)
                statement.setString(3, fechaEmi)
                statement.setString(4, fechaVen)
                statement.setString(5, estado)
                statement.setInt(6, conductorId)

                val filasAfectadas = statement.executeUpdate()
                exito = (filasAfectadas > 0)

            } catch (e: Exception) {
                e.printStackTrace()
                exito = false
            } finally {
                statement?.close()
                connection?.close()
            }
            exito
        }
    }
}