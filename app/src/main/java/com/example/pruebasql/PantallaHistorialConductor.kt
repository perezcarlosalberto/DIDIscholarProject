package com.example.pruebasql

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PantallaHistorialConductor : AppCompatActivity() {

    private lateinit var tablaHistorial: TableLayout
    private val con = ConnectSql()
    private var idConductor: Int = -1

    data class ViajeHistorial(
        val idViaje: Int,
        val destino: String,
        val costo: Double,
        val idPasajero: Int,
        val estadoPago: String? // NULL si no pagado
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial_conductor)

        idConductor = intent.getIntExtra("ID_USUARIO", -1)

        val toolbar: Toolbar = findViewById(R.id.toolbar_historial)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Historial y Pagos"

        tablaHistorial = findViewById(R.id.tablaHistorial)
        cargarHistorial()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun cargarHistorial() {
        lifecycleScope.launch {
            val historial = obtenerHistorial()
            llenarTabla(historial)
        }
    }

    private suspend fun obtenerHistorial(): List<ViajeHistorial> {
        return withContext(Dispatchers.IO) {
            val lista = mutableListOf<ViajeHistorial>()
            val conn = con.dbConn() ?: return@withContext lista
            try {
                // Left Join con Pago para saber si ya está pagado
                val query = """
                    SELECT V.ID_VIAJE, D.COLONIA, V.COSTO, V.ID_PASAJERO, P.ESTADO_PAGO
                    FROM VIAJE V 
                    JOIN DIRECCION D ON V.ID_DIRECCION = D.ID_DIRECCION
                    LEFT JOIN PAGO P ON V.ID_VIAJE = P.ID_VIAJE
                    WHERE V.ID_CONDUCTOR = ?
                """
                val ps = conn.prepareStatement(query)
                ps.setInt(1, idConductor)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    lista.add(ViajeHistorial(
                        rs.getInt("ID_VIAJE"),
                        rs.getString("COLONIA"),
                        rs.getDouble("COSTO"),
                        rs.getInt("ID_PASAJERO"),
                        rs.getString("ESTADO_PAGO")
                    ))
                }
                conn.close()
            } catch (e: SQLException) { e.printStackTrace() }
            lista
        }
    }

    private fun llenarTabla(datos: List<ViajeHistorial>) {
        tablaHistorial.removeAllViews()
        val header = TableRow(this)
        header.setBackgroundColor(Color.parseColor("#333333"))
        listOf("ID", "DESTINO", "COSTO", "ESTADO", "ACCIÓN").forEach {
            val tv = TextView(this)
            tv.text = it
            tv.setTextColor(Color.WHITE)
            tv.setPadding(20, 20, 20, 20)
            header.addView(tv)
        }
        tablaHistorial.addView(header)

        for (viaje in datos) {
            val row = TableRow(this)
            row.setBackgroundColor(Color.WHITE)

            row.addView(createTableCell(viaje.idViaje.toString()))
            row.addView(createTableCell(viaje.destino))
            row.addView(createTableCell("$${viaje.costo}"))

            val estadoTexto = if (viaje.estadoPago == "EXITOSO") "PAGADO" else "PENDIENTE"
            val tvEstado = createTableCell(estadoTexto)
            if (estadoTexto == "PAGADO") tvEstado.setTextColor(Color.GREEN) else tvEstado.setTextColor(Color.RED)
            row.addView(tvEstado)

            val btnAccion = Button(this, null, android.R.attr.buttonStyleSmall)

            if (viaje.estadoPago == "EXITOSO") {
                btnAccion.text = "CALIFICAR"
                btnAccion.setOnClickListener { mostrarDialogoCalificar(viaje) }
            } else {
                btnAccion.text = "COBRAR"
                btnAccion.setBackgroundColor(Color.parseColor("#FF9800"))
                btnAccion.setOnClickListener { cobrarViaje(viaje) }
            }
            row.addView(btnAccion)

            tablaHistorial.addView(row)
        }
    }

    private fun createTableCell(texto: String): TextView {
        val tv = TextView(this)
        tv.text = texto
        tv.setPadding(20, 20, 20, 20)
        tv.gravity = Gravity.CENTER
        return tv
    }

    private fun cobrarViaje(viaje: ViajeHistorial) {
        lifecycleScope.launch {
            val exito = withContext(Dispatchers.IO) {
                val conn = con.dbConn() ?: return@withContext false
                try {
                    val sql = "INSERT INTO PAGO (TIPO_PAGO, MONTO, ESTADO_PAGO, ID_VIAJE, ID_PASAJERO) VALUES ('EFECTIVO', ?, 'EXITOSO', ?, ?)"
                    val ps = conn.prepareStatement(sql)
                    ps.setDouble(1, viaje.costo)
                    ps.setInt(2, viaje.idViaje)
                    ps.setInt(3, viaje.idPasajero)
                    ps.executeUpdate()
                    conn.close()
                    true
                } catch (e: SQLException) { false }
            }
            if (exito) {
                Toast.makeText(this@PantallaHistorialConductor, "Cobro registrado", Toast.LENGTH_SHORT).show()
                cargarHistorial()
            }
        }
    }

    private fun mostrarDialogoCalificar(viaje: ViajeHistorial) {
        val builder = AlertDialog.Builder(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val ratingBar = RatingBar(this)
        ratingBar.numStars = 5
        ratingBar.stepSize = 1.0f
        layout.addView(ratingBar)

        val inputComentario = EditText(this)
        inputComentario.hint = "Comentario (Opcional)"
        layout.addView(inputComentario)

        builder.setView(layout)
        builder.setTitle("Calificar al Pasajero")

        builder.setPositiveButton("Enviar") { _, _ ->
            val estrellas = ratingBar.rating.toInt()
            val comentario = inputComentario.text.toString()
            enviarCalificacion(viaje, estrellas, comentario)
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun enviarCalificacion(viaje: ViajeHistorial, estrellas: Int, comentario: String) {
        lifecycleScope.launch {
            val exito = withContext(Dispatchers.IO) {
                val conn = con.dbConn() ?: return@withContext false
                try {
                    // GETDATE() es la función de SQL Server para la fecha actual
                    val sql = "INSERT INTO CALIFICACION (CALIFICACION, COMENTARIO, FECHA_CALIFICACION, ID_VIAJE, ID_PASAJERO, ID_CONDUCTOR) VALUES (?, ?, GETDATE(), ?, ?, ?)"
                    val ps = conn.prepareStatement(sql)
                    ps.setInt(1, estrellas)
                    ps.setString(2, comentario)
                    ps.setInt(3, viaje.idViaje)
                    ps.setInt(4, viaje.idPasajero)
                    ps.setInt(5, idConductor)
                    ps.executeUpdate()
                    conn.close()
                    true
                } catch (e: SQLException) { e.printStackTrace(); false }
            }
            if (exito) {
                Toast.makeText(this@PantallaHistorialConductor, "Calificación enviada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@PantallaHistorialConductor, "Error al calificar", Toast.LENGTH_SHORT).show()
            }
        }
    }
}