package com.example.pruebasql

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
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

class PantallaOperativo : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var btnVerPasajeros: Button
    private lateinit var btnVerConductores: Button
    private lateinit var tablaDatos: TableLayout
    private lateinit var tvTituloTabla: TextView

    private val con = ConnectSql()
    private var idUsuarioOperativo: Int = -1

    data class UsuarioParaTabla(
        val id: Int,
        val nombre: String,
        val apellido: String,
        val correo: String,
        val telefono: String,
        val tipo: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_operativo)

        idUsuarioOperativo = intent.getIntExtra("ID_USUARIO", -1)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Gestión de Usuarios"

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupDrawerContent(navigationView)

        btnVerPasajeros = findViewById(R.id.btnVerPasajeros)
        btnVerConductores = findViewById(R.id.btnVerConductores)
        tablaDatos = findViewById(R.id.tablaDatos)
        tvTituloTabla = findViewById(R.id.tvTituloTabla)

        btnVerPasajeros.setOnClickListener { cargarDatos("PASAJERO") }
        btnVerConductores.setOnClickListener { cargarDatos("CONDUCTOR") }

        // Carga inicial
        cargarDatos("PASAJERO")
    }

    private fun setupDrawerContent(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_gestion_usuarios -> true
                R.id.nav_gestion_incidencias -> {
                    val intent = Intent(this, PantallaGestionIncidencias::class.java)
                    intent.putExtra("ID_USUARIO", idUsuarioOperativo)
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

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun cargarDatos(tipoUsuario: String) {
        lifecycleScope.launch {
            try {
                val datos = obtenerUsuariosPorTipo(tipoUsuario)
                tvTituloTabla.text = "Lista de ${tipoUsuario}S"
                llenarTabla(datos)
            } catch (e: Exception) {
                Toast.makeText(this@PantallaOperativo, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun obtenerUsuariosPorTipo(tipo: String): List<UsuarioParaTabla> {
        return withContext(Dispatchers.IO) {
            val lista = mutableListOf<UsuarioParaTabla>()
            val connection = con.dbConn() ?: return@withContext lista
            try {
                // Join para asegurar consistencia
                val query = """
                    SELECT U.ID_USUARIO, U.NOMBRE_USUARIO, U.APELLIDO_USUARIO, U.CORREO, U.TELEFONO 
                    FROM USUARIO U
                    INNER JOIN ${tipo} T ON U.ID_USUARIO = T.ID_${tipo}
                    WHERE U.TIPO_USUARIO = ?
                """
                val ps = connection.prepareStatement(query)
                ps.setString(1, tipo)
                val rs = ps.executeQuery()

                while (rs.next()) {
                    lista.add(UsuarioParaTabla(
                        rs.getInt("ID_USUARIO"),
                        rs.getString("NOMBRE_USUARIO"),
                        rs.getString("APELLIDO_USUARIO"),
                        rs.getString("CORREO"),
                        rs.getString("TELEFONO"),
                        tipo
                    ))
                }
            } catch (e: SQLException) { e.printStackTrace() }
            finally { connection.close() }
            lista
        }
    }

    private fun llenarTabla(datos: List<UsuarioParaTabla>) {
        tablaDatos.removeAllViews()
        val rowHeader = TableRow(this)
        rowHeader.setBackgroundColor(Color.parseColor("#333333"))

        listOf("ID", "NOMBRE", "APELLIDO", "CORREO", "TEL", "ACCIONES").forEach {
            val tv = TextView(this)
            tv.text = it
            tv.setTextColor(Color.WHITE)
            tv.setPadding(16, 16, 16, 16)
            tv.setTypeface(null, Typeface.BOLD)
            rowHeader.addView(tv)
        }
        tablaDatos.addView(rowHeader)

        for ((index, usuario) in datos.withIndex()) {
            val row = TableRow(this)
            row.setBackgroundColor(if (index % 2 == 0) Color.WHITE else Color.parseColor("#F5F5F5"))

            row.addView(createTableCell(usuario.id.toString()))
            row.addView(createTableCell(usuario.nombre))
            row.addView(createTableCell(usuario.apellido))
            row.addView(createTableCell(usuario.correo))
            row.addView(createTableCell(usuario.telefono))

            val layoutBotones = LinearLayout(this)

            val btnMod = Button(this, null, android.R.attr.buttonStyleSmall)
            btnMod.text = "EDIT"
            btnMod.setOnClickListener { mostrarDialogoModificar(usuario) }
            layoutBotones.addView(btnMod)

            val btnDel = Button(this, null, android.R.attr.buttonStyleSmall)
            btnDel.text = "BORRAR"
            btnDel.setTextColor(Color.RED)
            btnDel.setOnClickListener { mostrarDialogoEliminar(usuario) }
            layoutBotones.addView(btnDel)

            row.addView(layoutBotones)
            tablaDatos.addView(row)
        }
    }

    private fun createTableCell(texto: String): TextView {
        val tv = TextView(this)
        tv.text = texto
        tv.setPadding(16, 16, 16, 16)
        return tv
    }

    private fun mostrarDialogoModificar(usuario: UsuarioParaTabla) {
        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_modificar_usuario, null)
        builder.setView(view)

        val etNombre = view.findViewById<EditText>(R.id.etModNombre)
        val etApellido = view.findViewById<EditText>(R.id.etModApellido)
        val etCorreo = view.findViewById<EditText>(R.id.etModCorreo)
        val etTelefono = view.findViewById<EditText>(R.id.etModTelefono)

        etNombre.setText(usuario.nombre)
        etApellido.setText(usuario.apellido)
        etCorreo.setText(usuario.correo)
        etTelefono.setText(usuario.telefono)

        builder.setTitle("Editar ${usuario.nombre}")
        builder.setPositiveButton("Guardar") { dialog, _ ->
            val nuevosDatos = UsuarioParaTabla(
                usuario.id,
                etNombre.text.toString(),
                etApellido.text.toString(),
                etCorreo.text.toString(),
                etTelefono.text.toString(),
                usuario.tipo
            )
            ejecutarModificacion(nuevosDatos)
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun ejecutarModificacion(usuario: UsuarioParaTabla) {
        lifecycleScope.launch {
            val exito = withContext(Dispatchers.IO) {
                val conn = con.dbConn() ?: return@withContext false
                try {
                    val sql = "UPDATE USUARIO SET NOMBRE_USUARIO=?, APELLIDO_USUARIO=?, CORREO=?, TELEFONO=? WHERE ID_USUARIO=?"
                    val ps = conn.prepareStatement(sql)
                    ps.setString(1, usuario.nombre)
                    ps.setString(2, usuario.apellido)
                    ps.setString(3, usuario.correo)
                    ps.setString(4, usuario.telefono)
                    ps.setInt(5, usuario.id)
                    val rows = ps.executeUpdate()
                    conn.close()
                    rows > 0
                } catch (e: SQLException) {
                    e.printStackTrace()
                    false
                }
            }
            if (exito) {
                Toast.makeText(this@PantallaOperativo, "Actualizado", Toast.LENGTH_SHORT).show()
                cargarDatos(usuario.tipo)
            } else {
                Toast.makeText(this@PantallaOperativo, "Error al actualizar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarDialogoEliminar(usuario: UsuarioParaTabla) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Eliminar Usuario")
            .setMessage("Se borrará todo: Vehículos, Documentos, Viajes y Pagos asociados a ${usuario.nombre}.\n\n¿Continuar?")
            .setPositiveButton("ELIMINAR") { _, _ -> ejecutarEliminacionCompleta(usuario) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- CORRECCIÓN CLAVE: BORRADO EN CASCADA ---
    private fun ejecutarEliminacionCompleta(usuario: UsuarioParaTabla) {
        lifecycleScope.launch {
            val exito = withContext(Dispatchers.IO) {
                var conn: Connection? = null
                try {
                    conn = con.dbConn()
                    if (conn == null) return@withContext false

                    conn.autoCommit = false // Iniciar Transacción

                    // 1. Eliminar dependencias según el tipo
                    if (usuario.tipo == "CONDUCTOR") {
                        // Borrar Documentos
                        conn.prepareStatement("DELETE FROM DOCUMENTO WHERE ID_CONDUCTOR = ?").apply {
                            setInt(1, usuario.id)
                            executeUpdate()
                        }
                        // Borrar Vehículos (y sus referencias si hubiera)
                        // (Si vehículo tiene hijos, borrarlos primero)
                        conn.prepareStatement("DELETE FROM VEHICULO WHERE ID_CONDUCTOR = ?").apply {
                            setInt(1, usuario.id)
                            executeUpdate()
                        }
                        // Borrar Calificaciones recibidas
                        conn.prepareStatement("DELETE FROM CALIFICACION WHERE ID_CONDUCTOR = ?").apply {
                            setInt(1, usuario.id)
                            executeUpdate()
                        }
                    } else if (usuario.tipo == "PASAJERO") {
                        // Borrar Pagos
                        conn.prepareStatement("DELETE FROM PAGO WHERE ID_PASAJERO = ?").apply {
                            setInt(1, usuario.id)
                            executeUpdate()
                        }
                        // Borrar Calificaciones hechas
                        conn.prepareStatement("DELETE FROM CALIFICACION WHERE ID_PASAJERO = ?").apply {
                            setInt(1, usuario.id)
                            executeUpdate()
                        }
                    }

                    // 2. Borrar Viajes (Donde sea conductor o pasajero)
                    val colViaje = if (usuario.tipo == "CONDUCTOR") "ID_CONDUCTOR" else "ID_PASAJERO"
                    conn.prepareStatement("DELETE FROM VIAJE WHERE $colViaje = ?").apply {
                        setInt(1, usuario.id)
                        executeUpdate()
                    }

                    // 3. Borrar de la tabla de Rol (Conductor/Pasajero)
                    val tablaRol = usuario.tipo
                    val colRol = "ID_${usuario.tipo}"
                    conn.prepareStatement("DELETE FROM $tablaRol WHERE $colRol = ?").apply {
                        setInt(1, usuario.id)
                        executeUpdate()
                    }

                    // 4. Finalmente, borrar el Usuario
                    conn.prepareStatement("DELETE FROM USUARIO WHERE ID_USUARIO = ?").apply {
                        setInt(1, usuario.id)
                        executeUpdate()
                    }

                    conn.commit() // Confirmar cambios
                    true
                } catch (e: SQLException) {
                    e.printStackTrace()
                    conn?.rollback()
                    false
                } finally {
                    conn?.close()
                }
            }

            if (exito) {
                Toast.makeText(this@PantallaOperativo, "Usuario eliminado correctamente", Toast.LENGTH_SHORT).show()
                cargarDatos(usuario.tipo)
            } else {
                Toast.makeText(this@PantallaOperativo, "Error SQL al eliminar. Revisa dependencias.", Toast.LENGTH_LONG).show()
            }
        }
    }
}