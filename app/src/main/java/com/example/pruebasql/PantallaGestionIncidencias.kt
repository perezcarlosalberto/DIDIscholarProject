package com.example.pruebasql

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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

class PantallaGestionIncidencias : AppCompatActivity() {

    // UI Elements
    private lateinit var spinnerIdViaje: Spinner // Nuevo Spinner
    private lateinit var spinnerTipo: Spinner
    private lateinit var etDescripcion: EditText
    private lateinit var etFecha: EditText
    private lateinit var spinnerEstado: Spinner
    private lateinit var btnGuardar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnEliminar: Button
    private lateinit var tablaIncidencias: TableLayout
    private lateinit var tvIdIncidencia: TextView

    private val con = ConnectSql()
    private var idUsuarioOperativo: Int = -1

    private var idIncidenciaSeleccionada: Int? = null

    // Lista para guardar los IDs de viajes cargados de la BD
    private var listaIdsViajes = mutableListOf<String>()

    private val tiposIncidencia = arrayOf("LEVE", "MODERADA", "GRAVE")
    private val estadosIncidencia = arrayOf("Pendiente", "En revisión", "En proceso", "Resuelta", "Cerrada")
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestion_incidencias)

        idUsuarioOperativo = intent.getIntExtra("ID_USUARIO", -1)

        setupToolbar()
        initViews()

        // Cargar datos iniciales
        etFecha.setText(sdf.format(Date()))
        cargarIdsViajesYConfigurarSpinners() // Cargamos viajes antes de configurar spinners

        // Listeners
        btnGuardar.setOnClickListener { procesarGuardado() }
        btnLimpiar.setOnClickListener { limpiarFormulario() }
        btnEliminar.setOnClickListener { confirmarEliminacion() }

        cargarIncidencias()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar_incidencias)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Gestión de Incidencias"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initViews() {
        spinnerIdViaje = findViewById(R.id.spinnerIdViaje) // Enlazamos el nuevo spinner
        spinnerTipo = findViewById(R.id.spinnerTipo)
        etDescripcion = findViewById(R.id.etDescripcion)
        etFecha = findViewById(R.id.etFecha)
        spinnerEstado = findViewById(R.id.spinnerEstado)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        btnEliminar = findViewById(R.id.btnEliminar)
        tablaIncidencias = findViewById(R.id.tablaIncidencias)
        tvIdIncidencia = findViewById(R.id.tvIdIncidenciaSeleccionada)
    }

    private fun cargarIdsViajesYConfigurarSpinners() {
        lifecycleScope.launch {
            // 1. Obtener IDs de Viajes desde la BD
            listaIdsViajes = obtenerIdsDeViajesDesdeBD()

            // 2. Configurar el adaptador del Spinner de Viajes
            if (listaIdsViajes.isNotEmpty()) {
                val adapterViajes = ArrayAdapter(this@PantallaGestionIncidencias, android.R.layout.simple_spinner_dropdown_item, listaIdsViajes)
                spinnerIdViaje.adapter = adapterViajes
            } else {
                Toast.makeText(this@PantallaGestionIncidencias, "No hay viajes registrados para reportar incidencias", Toast.LENGTH_LONG).show()
                val adapterVacio = ArrayAdapter(this@PantallaGestionIncidencias, android.R.layout.simple_spinner_dropdown_item, listOf("Sin Viajes"))
                spinnerIdViaje.adapter = adapterVacio
                spinnerIdViaje.isEnabled = false
                btnGuardar.isEnabled = false
            }

            // 3. Configurar los otros spinners fijos
            spinnerTipo.adapter = ArrayAdapter(this@PantallaGestionIncidencias, android.R.layout.simple_spinner_dropdown_item, tiposIncidencia)
            spinnerEstado.adapter = ArrayAdapter(this@PantallaGestionIncidencias, android.R.layout.simple_spinner_dropdown_item, estadosIncidencia)
        }
    }

    private suspend fun obtenerIdsDeViajesDesdeBD(): MutableList<String> {
        return withContext(Dispatchers.IO) {
            val ids = mutableListOf<String>()
            val connection = con.dbConn()
            if (connection != null) {
                try {
                    val query = "SELECT ID_VIAJE FROM VIAJE"
                    val rs = connection.createStatement().executeQuery(query)
                    while (rs.next()) {
                        ids.add(rs.getString("ID_VIAJE"))
                    }
                } catch (e: SQLException) { e.printStackTrace() }
                connection.close()
            }
            ids
        }
    }

    // --- Lógica de CRUD ---

    private fun procesarGuardado() {
        if (listaIdsViajes.isEmpty()) return

        val idViajeStr = spinnerIdViaje.selectedItem.toString() // Obtenemos del Spinner
        val descripcion = etDescripcion.text.toString().trim()
        val fecha = etFecha.text.toString().trim()
        val tipo = spinnerTipo.selectedItem.toString()
        val estado = spinnerEstado.selectedItem.toString()

        if (descripcion.isEmpty() || fecha.isEmpty()) {
            Toast.makeText(this, "Por favor completa la descripción y fecha", Toast.LENGTH_SHORT).show()
            return
        }
        val idViaje = idViajeStr.toIntOrNull()
        if (idViaje == null) {
            Toast.makeText(this, "ID de Viaje inválido", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val exito = if (idIncidenciaSeleccionada == null) {
                insertarIncidencia(idViaje, tipo, descripcion, fecha, estado)
            } else {
                actualizarIncidencia(idIncidenciaSeleccionada!!, idViaje, tipo, descripcion, fecha, estado)
            }

            if (exito) {
                val mensaje = if (idIncidenciaSeleccionada == null) "Registrada" else "Actualizada"
                Toast.makeText(this@PantallaGestionIncidencias, "Incidencia $mensaje con éxito", Toast.LENGTH_SHORT).show()
                limpiarFormulario()
                cargarIncidencias()
            } else {
                Toast.makeText(this@PantallaGestionIncidencias, "Error en la operación", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun insertarIncidencia(idViaje: Int, tipo: String, desc: String, fecha: String, estado: String): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = con.dbConn()
                val sql = "INSERT INTO INCIDENCIA (TIPO_INCIDENCIA, DESCRIPCION_INCIDENCIA, FECHA_INCIDENCIA, ESTADO_INCIDENCIA, ID_VIAJE, ID_USUARIO_REPORTA) VALUES (?, ?, ?, ?, ?, ?)"
                val ps = connection?.prepareStatement(sql)
                ps?.setString(1, tipo)
                ps?.setString(2, desc)
                ps?.setString(3, fecha)
                ps?.setString(4, estado)
                ps?.setInt(5, idViaje)
                ps?.setInt(6, idUsuarioOperativo)
                val filas = ps?.executeUpdate() ?: 0
                ps?.close()
                filas > 0
            } catch (e: SQLException) {
                e.printStackTrace()
                false
            } finally {
                connection?.close()
            }
        }
    }

    private suspend fun actualizarIncidencia(id: Int, idViaje: Int, tipo: String, desc: String, fecha: String, estado: String): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = con.dbConn()
                val sql = "UPDATE INCIDENCIA SET TIPO_INCIDENCIA = ?, DESCRIPCION_INCIDENCIA = ?, FECHA_INCIDENCIA = ?, ESTADO_INCIDENCIA = ?, ID_VIAJE = ? WHERE ID_INCIDENCIA = ?"
                val ps = connection?.prepareStatement(sql)
                ps?.setString(1, tipo)
                ps?.setString(2, desc)
                ps?.setString(3, fecha)
                ps?.setString(4, estado)
                ps?.setInt(5, idViaje)
                ps?.setInt(6, id)
                val filas = ps?.executeUpdate() ?: 0
                ps?.close()
                filas > 0
            } catch (e: SQLException) { e.printStackTrace(); false } finally { connection?.close() }
        }
    }

    private fun confirmarEliminacion() {
        if (idIncidenciaSeleccionada == null) return
        AlertDialog.Builder(this)
            .setTitle("Eliminar Incidencia")
            .setMessage("¿Borrar incidencia #$idIncidenciaSeleccionada?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    if (eliminarIncidenciaEnBD(idIncidenciaSeleccionada!!)) {
                        Toast.makeText(this@PantallaGestionIncidencias, "Eliminada", Toast.LENGTH_SHORT).show()
                        limpiarFormulario()
                        cargarIncidencias()
                    } else {
                        Toast.makeText(this@PantallaGestionIncidencias, "Error al eliminar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private suspend fun eliminarIncidenciaEnBD(id: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val conn = con.dbConn() ?: return@withContext false
            try {
                val ps = conn.prepareStatement("DELETE FROM INCIDENCIA WHERE ID_INCIDENCIA = ?")
                ps.setInt(1, id)
                val filas = ps.executeUpdate()
                ps.close()
                conn.close()
                filas > 0
            } catch (e: SQLException) { e.printStackTrace(); false }
        }
    }

    // --- UI Helpers ---

    private fun limpiarFormulario() {
        idIncidenciaSeleccionada = null
        tvIdIncidencia.text = "Nueva Incidencia"
        if (listaIdsViajes.isNotEmpty()) spinnerIdViaje.setSelection(0)
        etDescripcion.setText("")
        spinnerTipo.setSelection(0)
        spinnerEstado.setSelection(0)
        btnGuardar.text = "Registrar"
        btnGuardar.setBackgroundColor(Color.parseColor("#FF7D00"))
        btnEliminar.visibility = View.GONE
        etFecha.setText(sdf.format(Date()))
    }

    private fun cargarIncidenciaParaEditar(id: String, idViaje: String, tipo: String, desc: String, fecha: String, estado: String) {
        idIncidenciaSeleccionada = id.toInt()
        tvIdIncidencia.text = "Editando Incidencia #$id"

        // Seleccionar el ID de Viaje correcto en el Spinner
        val viajeIdx = listaIdsViajes.indexOf(idViaje)
        if (viajeIdx >= 0) spinnerIdViaje.setSelection(viajeIdx)

        etDescripcion.setText(desc)
        etFecha.setText(fecha)
        val tipoIdx = tiposIncidencia.indexOf(tipo)
        if (tipoIdx >= 0) spinnerTipo.setSelection(tipoIdx)
        val estadoIdx = estadosIncidencia.indexOf(estado)
        if (estadoIdx >= 0) spinnerEstado.setSelection(estadoIdx)

        btnGuardar.text = "Actualizar"
        btnGuardar.setBackgroundColor(Color.parseColor("#4CAF50"))
        btnEliminar.visibility = View.VISIBLE
    }

    data class IncidenciaParaTabla(val id: Int, val tipo: String, val descripcion: String, val fecha: String, val estado: String, val idViaje: Int)

    private fun cargarIncidencias() {
        lifecycleScope.launch {
            val lista = withContext(Dispatchers.IO) {
                val data = mutableListOf<IncidenciaParaTabla>()
                val conn = con.dbConn()
                if (conn != null) {
                    try {
                        val rs = conn.createStatement().executeQuery("SELECT * FROM INCIDENCIA")
                        while (rs.next()) {
                            data.add(IncidenciaParaTabla(
                                rs.getInt("ID_INCIDENCIA"),
                                rs.getString("TIPO_INCIDENCIA"),
                                rs.getString("DESCRIPCION_INCIDENCIA"),
                                rs.getString("FECHA_INCIDENCIA"),
                                rs.getString("ESTADO_INCIDENCIA"),
                                rs.getInt("ID_VIAJE")
                            ))
                        }
                    } catch (e: SQLException) { e.printStackTrace() }
                    conn.close()
                }
                data
            }
            llenarTabla(lista)
        }
    }

    private fun llenarTabla(datos: List<IncidenciaParaTabla>) {
        tablaIncidencias.removeAllViews()
        val header = TableRow(this)
        header.setBackgroundColor(Color.parseColor("#333333"))
        listOf("ID", "VIAJE", "TIPO", "DESCRIPCIÓN", "FECHA", "ESTADO", "ACCIÓN").forEach {
            val tv = TextView(this)
            tv.text = it
            tv.setTextColor(Color.WHITE)
            tv.setPadding(16, 16, 16, 16)
            header.addView(tv)
        }
        tablaIncidencias.addView(header)

        for (fila in datos) {
            val row = TableRow(this)
            row.setBackgroundColor(Color.WHITE)

            // Celdas
            row.addView(createTableCell(fila.id.toString()))
            row.addView(createTableCell(fila.idViaje.toString()))
            row.addView(createTableCell(fila.tipo))
            row.addView(createTableCell(fila.descripcion))
            row.addView(createTableCell(fila.fecha))
            row.addView(createTableCell(fila.estado))

            // Botón Editar
            val btnEdit = Button(this, null, android.R.attr.buttonStyleSmall)
            btnEdit.text = "Editar"
            btnEdit.setOnClickListener {
                cargarIncidenciaParaEditar(
                    fila.id.toString(), fila.idViaje.toString(), fila.tipo, fila.descripcion, fila.fecha, fila.estado
                )
            }
            row.addView(btnEdit)
            tablaIncidencias.addView(row)
        }
    }

    private fun createTableCell(texto: String): TextView {
        val tv = TextView(this)
        tv.text = texto
        tv.setPadding(20, 20, 20, 20)
        tv.gravity = Gravity.CENTER
        return tv
    }

    private fun esFechaValida(fecha: String): Boolean {
        // Validación básica de formato YYYY-MM-DD
        return fecha.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))
    }
}