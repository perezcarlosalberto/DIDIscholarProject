package com.example.pruebasql

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection

class PantallaPago : AppCompatActivity() {

    private var idViaje: Int = -1
    private var idPasajero: Int = -1
    private var monto: Double = 0.0

    private val con = ConnectSql()
    private lateinit var spinnerTipoPago: Spinner
    private lateinit var btnConfirmarPago: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_pago)

        idViaje = intent.getIntExtra("ID_VIAJE", -1)
        idPasajero = intent.getIntExtra("ID_PASAJERO", -1)
        monto = intent.getDoubleExtra("MONTO_VIAJE", 0.0)

        if (idViaje == -1) {
            Toast.makeText(this, "Error en datos del viaje", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        spinnerTipoPago = findViewById(R.id.spinnerTipoPago)
        btnConfirmarPago = findViewById(R.id.btnConfirmarPago)
        findViewById<TextView>(R.id.tvMontoAPagar).text = "Total: $${String.format("%.2f", monto)}"

        val tiposPago = arrayOf("EFECTIVO", "TARJETA")
        spinnerTipoPago.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tiposPago)

        btnConfirmarPago.setOnClickListener {
            realizarPago()
        }
    }

    private fun realizarPago() {
        val tipoPago = spinnerTipoPago.selectedItem.toString()
        btnConfirmarPago.isEnabled = false
        btnConfirmarPago.text = "Pagando..."

        lifecycleScope.launch {
            val exito = withContext(Dispatchers.IO) {
                var connection: Connection? = null
                try {
                    connection = con.dbConn()
                    val sql = "INSERT INTO PAGO (TIPO_PAGO, MONTO, ESTADO_PAGO, ID_VIAJE, ID_PASAJERO) VALUES (?, ?, 'EXITOSO', ?, ?)"
                    val ps = connection?.prepareStatement(sql)
                    ps?.setString(1, tipoPago)
                    ps?.setDouble(2, monto)
                    ps?.setInt(3, idViaje)
                    ps?.setInt(4, idPasajero)

                    val filas = ps?.executeUpdate() ?: 0
                    ps?.close()
                    connection?.close()
                    filas > 0
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (exito) {
                Toast.makeText(this@PantallaPago, "¡Pago Exitoso! Viaje Terminado.", Toast.LENGTH_LONG).show()
                finish() // Cierra pantalla de pago
                // Aquí podrías añadir lógica para cerrar también la pantalla de espera del pasajero
            } else {
                Toast.makeText(this@PantallaPago, "Error al pagar", Toast.LENGTH_LONG).show()
                btnConfirmarPago.isEnabled = true
                btnConfirmarPago.text = "Confirmar Pago"
            }
        }
    }
}