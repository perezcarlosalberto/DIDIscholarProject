package com.example.pruebasql

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException

class PantallaConductor : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var tablaViajesDisponibles: TableLayout

    private var idConductor: Int = -1
    private val con = ConnectSql()

    data class ViajeDisponible(
        val idViaje: Int,
        val destino: String,
        val costo: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_conductor)

        idConductor = intent.getIntExtra("ID_USUARIO", -1)

        // Configurar Toolbar y Drawer
        toolbar = findViewById(R.id.toolbar_conductor)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Viajes Disponibles"

        drawerLayout = findViewById(R.id.drawer_layout_conductor)
        navigationView = findViewById(R.id.nav_view_conductor)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupDrawerContent(navigationView)

        tablaViajesDisponibles = findViewById(R.id.tablaViajesDisponibles)

        findViewById<Button>(R.id.btnRefrescarViajes).setOnClickListener {
            cargarViajesDisponibles()
        }

        cargarViajesDisponibles()
    }

    private fun setupDrawerContent(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_inicio_conductor -> true

                R.id.nav_historial_conductor -> {
                    val intent = Intent(this, PantallaHistorialConductor::class.java)
                    intent.putExtra("ID_USUARIO", idConductor)
                    startActivity(intent)
                    true
                }

                // --- NUEVO CASO AGREGADO ---
                R.id.nav_agregar_vehiculo -> {
                    val intent = Intent(this, Vehiculo_Conductor::class.java)
                    intent.putExtra("ID_USUARIO", idConductor)
                    startActivity(intent)
                    true
                }
                // ---------------------------

                R.id.nav_subir_documentacion -> {
                    val intent = Intent(this, DocumentoConductor::class.java)
                    intent.putExtra("ID_USUARIO", idConductor)
                    startActivity(intent)
                    true
                }
                R.id.nav_cerrar_sesion -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun cargarViajesDisponibles() {
        lifecycleScope.launch {
            val viajes = obtenerViajesDisponibles()
            llenarTabla(viajes)
        }
    }

    private suspend fun obtenerViajesDisponibles(): List<ViajeDisponible> {
        return withContext(Dispatchers.IO) {
            val lista = mutableListOf<ViajeDisponible>()
            val connection = con.dbConn() ?: return@withContext lista
            try {
                // Viajes sin conductor asignado
                val query = """
                    SELECT V.ID_VIAJE, D.CALLE + ', ' + D.COLONIA AS DESTINO, V.COSTO 
                    FROM VIAJE V JOIN DIRECCION D ON V.ID_DIRECCION = D.ID_DIRECCION
                    WHERE V.ID_CONDUCTOR IS NULL OR V.ID_CONDUCTOR = 0
                """
                val rs = connection.createStatement().executeQuery(query)
                while (rs.next()) {
                    lista.add(ViajeDisponible(
                        rs.getInt("ID_VIAJE"),
                        rs.getString("DESTINO"),
                        rs.getDouble("COSTO")
                    ))
                }
                connection.close()
            } catch (e: SQLException) { e.printStackTrace() }
            lista
        }
    }

    private fun llenarTabla(viajes: List<ViajeDisponible>) {
        tablaViajesDisponibles.removeAllViews()
        val header = TableRow(this)
        header.setBackgroundColor(Color.parseColor("#333333"))
        listOf("ID", "DESTINO", "COSTO", "ACCIÓN").forEach {
            val tv = TextView(this)
            tv.text = it
            tv.setTextColor(Color.WHITE)
            tv.setPadding(20, 20, 20, 20)
            header.addView(tv)
        }
        tablaViajesDisponibles.addView(header)

        for (viaje in viajes) {
            val row = TableRow(this)
            row.setBackgroundColor(Color.WHITE)

            row.addView(createTableCell(viaje.idViaje.toString()))
            row.addView(createTableCell(viaje.destino))
            row.addView(createTableCell("$${String.format("%.2f", viaje.costo)}"))

            val btnAceptar = Button(this, null, android.R.attr.buttonStyleSmall)
            btnAceptar.text = "ACEPTAR"
            btnAceptar.setBackgroundColor(Color.parseColor("#4CAF50"))
            btnAceptar.setOnClickListener { aceptarViaje(viaje.idViaje) }
            row.addView(btnAceptar)

            tablaViajesDisponibles.addView(row)
        }
    }

    private fun createTableCell(texto: String): TextView {
        val tv = TextView(this)
        tv.text = texto
        tv.setPadding(20, 20, 20, 20)
        tv.gravity = Gravity.CENTER_VERTICAL
        return tv
    }

    private fun aceptarViaje(idViaje: Int) {
        lifecycleScope.launch {
            val exito = withContext(Dispatchers.IO) {
                val conn = con.dbConn() ?: return@withContext false
                try {
                    val sql = "UPDATE VIAJE SET ID_CONDUCTOR = ? WHERE ID_VIAJE = ?"
                    val ps = conn.prepareStatement(sql)
                    ps.setInt(1, idConductor)
                    ps.setInt(2, idViaje)
                    val filas = ps.executeUpdate()
                    conn.close()
                    filas > 0
                } catch (e: SQLException) { false }
            }
            if (exito) {
                Toast.makeText(this@PantallaConductor, "Viaje aceptado", Toast.LENGTH_SHORT).show()
                cargarViajesDisponibles()
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
        else super.onBackPressed()
    }
}