package com.example.pruebasql

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement

class Vehiculo_Conductor : AppCompatActivity() {

    private lateinit var etNombreVehiculo: EditText
    private lateinit var etModeloVehiculo: EditText
    private lateinit var etColorVehiculo: EditText
    private lateinit var etAnioVehiculo: EditText
    private lateinit var btnRegistrarVehiculo: Button

    private val con = ConnectSql()
    private var idConductor: Int = -1

    private val MAX_LEN_NOMBRE = 50
    private val MAX_LEN_MODELO = 50
    private val MAX_LEN_COLOR = 30
    private val LEN_ANIO = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CORRECCIÓN: Enlazamos con el XML que acabamos de crear en el Paso 1
        setContentView(R.layout.activity_vehiculo_conductor)

        idConductor = intent.getIntExtra("ID_USUARIO", -1)

        if (idConductor == -1) {
            Toast.makeText(this, "Error crítico: No se recibió el ID del conductor", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        etNombreVehiculo = findViewById(R.id.etNombre_vehiculo)
        etModeloVehiculo = findViewById(R.id.etModelo_vehiculo)
        etColorVehiculo = findViewById(R.id.et_Color_vehiculo)
        etAnioVehiculo = findViewById(R.id.etFecha_vehiculo)
        btnRegistrarVehiculo = findViewById(R.id.btnRegistrar_vehiculo)

        // Filtros de longitud
        etNombreVehiculo.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN_NOMBRE))
        etModeloVehiculo.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN_MODELO))
        etColorVehiculo.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN_COLOR))
        etAnioVehiculo.filters = arrayOf(InputFilter.LengthFilter(LEN_ANIO))

        btnRegistrarVehiculo.setOnClickListener {
            intentarRegistroVehiculo()
        }
    }

    private fun intentarRegistroVehiculo() {
        val nombre = etNombreVehiculo.text.toString().trim()
        val modelo = etModeloVehiculo.text.toString().trim()
        val color = etColorVehiculo.text.toString().trim()
        val anioStr = etAnioVehiculo.text.toString().trim()

        // Validaciones
        if (nombre.isEmpty() || modelo.isEmpty() || color.isEmpty() || anioStr.isEmpty()) {
            Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        if (anioStr.length != 4) {
            Toast.makeText(this, "El año debe tener 4 dígitos", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            btnRegistrarVehiculo.isEnabled = false
            btnRegistrarVehiculo.text = "Guardando..."

            val exito = insertarVehiculoEnBD(nombre, modelo, color, anioStr, idConductor)

            if (exito) {
                Toast.makeText(this@Vehiculo_Conductor, "¡Vehículo registrado!", Toast.LENGTH_SHORT).show()
                // Regresar a la pantalla principal del conductor
                val intent = Intent(this@Vehiculo_Conductor, PantallaConductor::class.java)
                intent.putExtra("ID_USUARIO", idConductor)
                // Limpiar el stack para que no pueda volver atrás al formulario
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this@Vehiculo_Conductor, "Error al registrar. Verifica tu conexión.", Toast.LENGTH_LONG).show()
                btnRegistrarVehiculo.isEnabled = true
                btnRegistrarVehiculo.text = "Guardar Vehículo"
            }
        }
    }

    private suspend fun insertarVehiculoEnBD(nombre: String, modelo: String, color: String, anio: String, conductorId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            var statement: PreparedStatement? = null
            var exito = false

            try {
                connection = con.dbConn()
                if (connection != null) {
                    val sql = "INSERT INTO VEHICULO (NOMBRE_VEHICULO, MODELO, COLOR, AÑO, ID_CONDUCTOR) VALUES (?, ?, ?, ?, ?)"
                    statement = connection.prepareStatement(sql)
                    statement.setString(1, nombre)
                    statement.setString(2, modelo)
                    statement.setString(3, color)
                    statement.setString(4, anio)
                    statement.setInt(5, conductorId)

                    val filas = statement.executeUpdate()
                    exito = (filas > 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                statement?.close()
                connection?.close()
            }
            exito
        }
    }
}