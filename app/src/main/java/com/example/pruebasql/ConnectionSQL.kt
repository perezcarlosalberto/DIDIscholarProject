package com.example.pruebasql // <--- ESTA ES LA CLAVE: debe ser minúscula

import android.util.Log
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class ConnectSql {

    private val ip = "" // Verifica que esta sea tu IP actual
    private val port = "TUPUERTO"
    private val db = "DIDI"
    private val username = "TU USUARIO"
    private val password = "TU_CONTRASEÑA"

    fun dbConn(): Connection? {

        var conn: Connection? = null
        var connString: String

        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")

            connString = "jdbc:jtds:sqlserver://$ip:$port/$db"

            conn = DriverManager.getConnection(connString, username, password)
            Log.d("Conexion", "¡Conexión exitosa con JTDS!")

        } catch (ex: SQLException) {
            Log.e("Error SQL:", ex.message!!)
        } catch (ex1: ClassNotFoundException) {
            Log.e("Error Driver:", "No se encontró el driver: ${ex1.message!!}")
        } catch (ex2: Exception) {
            Log.e("Error General:", ex2.message!!)
        }

        return conn
    }
}