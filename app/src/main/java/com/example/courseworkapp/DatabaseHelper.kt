package com.example.courseworkapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager

object DatabaseHelper {
    private const val URL = "jdbc:mysql://10.0.2.2:3306/power_system?characterEncoding=utf8"
    private const val USER = "root"
    private const val PASS = ""

    private val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val defaultOnSequence = "1".repeat(24)

    suspend fun connectAndExecute(action: (Connection) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn = DriverManager.getConnection(URL, USER, PASS)
                action(conn)
                conn.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun authenticate(login: String, pass: String): UserRole {
        return withContext(Dispatchers.IO) {
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn = DriverManager.getConnection(URL, USER, PASS)

                val adminStmt = conn.prepareStatement("SELECT id FROM admins WHERE login = ? AND password = ?")
                adminStmt.setString(1, login)
                adminStmt.setString(2, pass)
                if (adminStmt.executeQuery().next()) {
                    conn.close()
                    return@withContext UserRole.ADMIN
                }

                val userStmt = conn.prepareStatement("SELECT id FROM users WHERE login = ? AND password = ?")
                userStmt.setString(1, login)
                userStmt.setString(2, pass)
                if (userStmt.executeQuery().next()) {
                    conn.close()
                    return@withContext UserRole.USER
                }
                conn.close()
                UserRole.NONE
            } catch (e: Exception) { UserRole.NONE }
        }
    }

    suspend fun registerUser(login: String, pass: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                connectAndExecute { conn ->
                    val stmt = conn.prepareStatement("INSERT INTO users (login, password) VALUES (?, ?)")
                    stmt.setString(1, login)
                    stmt.setString(2, pass)
                    stmt.executeUpdate()
                }
                true
            } catch (e: Exception) { false }
        }
    }

    suspend fun createAddress(name: String, groupId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn = DriverManager.getConnection(URL, USER, PASS)

                // 1. Додаємо адресу
                val stmt = conn.prepareStatement("INSERT INTO addresses (physical_address, group_id) VALUES (?, ?)")
                stmt.setString(1, name)
                stmt.setInt(2, groupId)
                stmt.executeUpdate()

                // 2. Гарантуємо, що для цієї групи є розклад (логіка з консольного додатку)
                ensureGroupScheduleExists(conn, groupId)

                conn.close()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun ensureGroupScheduleExists(conn: Connection, group: Int) {
        val checkStmt = conn.prepareStatement("SELECT count(*) FROM group_schedules WHERE group_id = ?")
        checkStmt.setInt(1, group)
        val rs = checkStmt.executeQuery()
        rs.next()
        val count = rs.getInt(1)

        if (count < 7) {
            val insertStmt = conn.prepareStatement(
                "INSERT IGNORE INTO group_schedules (group_id, day_name, schedule_str) VALUES (?, ?, ?)"
            )
            for (day in daysOfWeek) {
                insertStmt.setInt(1, group)
                insertStmt.setString(2, day)
                insertStmt.setString(3, defaultOnSequence)
                insertStmt.executeUpdate()
            }
        }
    }

    suspend fun updateGroupSchedule(groupId: Int, day: String, schedule: String) {
        withContext(Dispatchers.IO) {
            connectAndExecute { conn ->
                // Переконуємось, що записи існують, перш ніж оновлювати
                ensureGroupScheduleExists(conn, groupId)

                val q = "UPDATE group_schedules SET schedule_str = ? WHERE group_id = ? AND day_name = ?"
                val stmt = conn.prepareStatement(q)
                stmt.setString(1, schedule)
                stmt.setInt(2, groupId)
                stmt.setString(3, day)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun getScheduleForGroup(groupId: Int): List<String> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<String>()
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn = DriverManager.getConnection(URL, USER, PASS)
                val q = "SELECT day_name, schedule_str FROM group_schedules WHERE group_id = ? ORDER BY FIELD(day_name, 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun')"
                val stmt = conn.prepareStatement(q)
                stmt.setInt(1, groupId)
                val rs = stmt.executeQuery()
                while(rs.next()) {
                    list.add("${rs.getString("day_name")}: ${rs.getString("schedule_str")}")
                }
                conn.close()
            } catch (e: Exception) { e.printStackTrace() }
            list
        }
    }

    suspend fun getAllAddressesForSelection(): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            val map = mutableMapOf<String, Int>()
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn = DriverManager.getConnection(URL, USER, PASS)
                val rs = conn.createStatement().executeQuery("SELECT id, physical_address FROM addresses ORDER BY physical_address")
                while (rs.next()) {
                    map[rs.getString("physical_address")] = rs.getInt("id")
                }
                conn.close()
            } catch (e: Exception) { }
            map
        }
    }

    suspend fun getAddressDetails(id: Int): Triple<String, String, Boolean>? {
        return withContext(Dispatchers.IO) {
            var result: Triple<String, String, Boolean>? = null
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn = DriverManager.getConnection(URL, USER, PASS)
                val stmt = conn.prepareStatement("SELECT physical_address, generator_status, generator_auto FROM addresses WHERE id = ?")
                stmt.setInt(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    result = Triple(rs.getString(1), rs.getString(2), rs.getBoolean(3))
                }
                conn.close()
            } catch (e: Exception) {}
            result
        }
    }

    suspend fun addUserAddress(login: String, addressId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                connectAndExecute { conn ->
                    val uStmt = conn.prepareStatement("SELECT id FROM users WHERE login = ?")
                    uStmt.setString(1, login)
                    val rs = uStmt.executeQuery()
                    if (rs.next()) {
                        val userId = rs.getInt(1)
                        val insStmt = conn.prepareStatement("INSERT INTO user_addresses (user_id, address_id) VALUES (?, ?)")
                        insStmt.setInt(1, userId)
                        insStmt.setInt(2, addressId)
                        insStmt.executeUpdate()
                    }
                }
                true
            } catch (e: Exception) { false }
        }
    }

    suspend fun removeUserAddress(login: String, addressId: Int): Boolean { /* ... код той самий ... */
        return withContext(Dispatchers.IO) {
            try {
                connectAndExecute { conn ->
                    val uStmt = conn.prepareStatement("SELECT id FROM users WHERE login = ?")
                    uStmt.setString(1, login)
                    val rs = uStmt.executeQuery()
                    if (rs.next()) {
                        val userId = rs.getInt(1)
                        val delStmt = conn.prepareStatement("DELETE FROM user_addresses WHERE user_id = ? AND address_id = ?")
                        delStmt.setInt(1, userId)
                        delStmt.setInt(2, addressId)
                        delStmt.executeUpdate()
                    }
                }
                true
            } catch (e: Exception) { false }
        }
    }

    suspend fun updateGeneratorStatus(addressId: Int, status: String) {
        withContext(Dispatchers.IO) {
            connectAndExecute { conn ->
                val stmt = conn.prepareStatement("UPDATE addresses SET generator_status = ? WHERE id = ?")
                stmt.setString(1, status)
                stmt.setInt(2, addressId)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun toggleGeneratorAuto(addressId: Int, currentAuto: Boolean) {
        withContext(Dispatchers.IO) {
            connectAndExecute { conn ->
                val stmt = conn.prepareStatement("UPDATE addresses SET generator_auto = ? WHERE id = ?")
                stmt.setBoolean(1, !currentAuto)
                stmt.setInt(2, addressId)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun toggleGeneratorManual(addressId: Int, currentManual: Boolean) {
        withContext(Dispatchers.IO) {
            connectAndExecute { conn ->
                val stmt = conn.prepareStatement("UPDATE addresses SET is_manual_run = ? WHERE id = ?")
                stmt.setBoolean(1, !currentManual)
                stmt.setInt(2, addressId)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun getSystemLogs(): List<String> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<String>()
            connectAndExecute { conn ->
                val rs = conn.createStatement().executeQuery("SELECT message FROM system_logs ORDER BY id DESC LIMIT 10")
                while(rs.next()) list.add(rs.getString("message"))
            }
            list
        }
    }
}