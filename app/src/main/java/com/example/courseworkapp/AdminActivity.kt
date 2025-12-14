package com.example.courseworkapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {
    private lateinit var tvConsole: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        tvConsole = findViewById(R.id.tvConsole)

        findViewById<Button>(R.id.btnShutdownGroup).setOnClickListener { showGroupInputDialog() }
        findViewById<Button>(R.id.btnShutdownAll).setOnClickListener { performEmergencyShutdown() }
        findViewById<Button>(R.id.btnRestore).setOnClickListener { restorePower() }

        findViewById<Button>(R.id.btnCreateAddress).setOnClickListener { showCreateAddressDialog() }
        findViewById<Button>(R.id.btnManageGenerators).setOnClickListener { showGeneratorSelectDialog() }
        findViewById<Button>(R.id.btnManageSchedule).setOnClickListener { showEditScheduleDialog() }
        findViewById<Button>(R.id.btnViewSchedule).setOnClickListener { showViewScheduleDialog() }

        findViewById<Button>(R.id.btnLogs).setOnClickListener { fetchLogs() }
        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }
    }

    private fun showCreateAddressDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val inputName = EditText(this)
        inputName.hint = "Physical Address (e.g. Kyiv_Main_1)"
        layout.addView(inputName)

        val inputGroup = EditText(this)
        inputGroup.hint = "Group ID (0-9)"
        inputGroup.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        layout.addView(inputGroup)

        AlertDialog.Builder(this)
            .setTitle("Create New Address")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = inputName.text.toString()
                val grpStr = inputGroup.text.toString()
                if (name.isNotEmpty() && grpStr.isNotEmpty()) {
                    createAddress(name, grpStr.toInt())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createAddress(name: String, group: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val success = DatabaseHelper.createAddress(name, group)
            if (success) {
                logToScreen("Address '$name' (Group $group) created.")
                logSystemAction("Admin created address $name")
            } else {
                logToScreen("Error creating address (Duplicate name?).")
            }
        }
    }

    private fun showGeneratorSelectDialog() {
        CoroutineScope(Dispatchers.Main).launch {
            val allAddresses = DatabaseHelper.getAllAddressesForSelection()
            if (allAddresses.isEmpty()) {
                Toast.makeText(this@AdminActivity, "No addresses found", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val names = allAddresses.keys.toTypedArray()
            AlertDialog.Builder(this@AdminActivity)
                .setTitle("Select Address to Manage")
                .setItems(names) { _, which ->
                    val selectedName = names[which]
                    val id = allAddresses[selectedName] ?: return@setItems
                    manageSpecificGenerator(id, selectedName)
                }
                .show()
        }
    }

    private fun manageSpecificGenerator(id: Int, name: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val details = DatabaseHelper.getAddressDetails(id)
            if (details == null) return@launch

            val (addrName, status, auto) = details
            val options = arrayOf(
                "Set NONE (No Gen)",
                "Set WORKING (Operational)",
                "Set BROKEN (Broken)",
                "Toggle Auto-Mode (Current: $auto)"
            )

            AlertDialog.Builder(this@AdminActivity)
                .setTitle("Manage: $addrName ($status)")
                .setItems(options) { _, which ->
                    CoroutineScope(Dispatchers.Main).launch {
                        when (which) {
                            0 -> DatabaseHelper.updateGeneratorStatus(id, "NONE")
                            1 -> DatabaseHelper.updateGeneratorStatus(id, "WORKING")
                            2 -> DatabaseHelper.updateGeneratorStatus(id, "BROKEN")
                            3 -> DatabaseHelper.toggleGeneratorAuto(id, auto)
                        }
                        logToScreen("Updated generator settings for $addrName")
                        logSystemAction("Admin updated generator for $addrName")
                    }
                }
                .show()
        }
    }

    private fun showEditScheduleDialog() {
        val inputGroup = EditText(this)
        inputGroup.hint = "Enter Group (0-9)"
        inputGroup.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Step 1: Select Group")
            .setView(inputGroup)
            .setPositiveButton("Next") { _, _ ->
                val gStr = inputGroup.text.toString()
                if (gStr.isNotEmpty()) showDaySelectionDialog(gStr.toInt())
            }
            .show()
    }

    private fun showDaySelectionDialog(group: Int) {
        val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        AlertDialog.Builder(this)
            .setTitle("Step 2: Select Day")
            .setItems(days) { _, which ->
                showScheduleInputDialog(group, days[which])
            }
            .show()
    }

    private fun showScheduleInputDialog(group: Int, day: String) {
        val input = EditText(this)
        input.hint = "24 chars (1=ON, 0=OFF)"
        input.setText("1".repeat(24)) // Default

        AlertDialog.Builder(this)
            .setTitle("Edit Schedule: Grp $group, $day")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val sched = input.text.toString()
                if (sched.length == 24 && sched.all { it == '0' || it == '1' }) {
                    saveSchedule(group, day, sched)
                } else {
                    Toast.makeText(this, "Error: Must be 24 chars of 0/1", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun saveSchedule(group: Int, day: String, schedule: String) {
        CoroutineScope(Dispatchers.Main).launch {
            DatabaseHelper.updateGroupSchedule(group, day, schedule)
            logToScreen("Schedule updated for Group $group ($day)")
            logSystemAction("Admin updated schedule for Group $group")
        }
    }

    private fun showViewScheduleDialog() {
        val inputGroup = EditText(this)
        inputGroup.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputGroup.hint = "Group ID"

        AlertDialog.Builder(this)
            .setTitle("View Schedule")
            .setView(inputGroup)
            .setPositiveButton("View") { _, _ ->
                val gStr = inputGroup.text.toString()
                if (gStr.isNotEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val list = DatabaseHelper.getScheduleForGroup(gStr.toInt())
                        val msg = if (list.isEmpty()) "No schedule found" else list.joinToString("\n")
                        AlertDialog.Builder(this@AdminActivity)
                            .setTitle("Schedule Group ${gStr}")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun performEmergencyShutdown() {
        AlertDialog.Builder(this)
            .setTitle("CONFIRM MASSIVE SHUTDOWN")
            .setMessage("Turn OFF all groups except 0?")
            .setPositiveButton("YES") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    DatabaseHelper.connectAndExecute { conn ->
                        val stmt = conn.prepareStatement("UPDATE addresses SET current_power_status = 0 WHERE group_id != 0")
                        stmt.executeUpdate()
                    }
                    logSystemAction("MASSIVE EMERGENCY SHUTDOWN")
                    logToScreen("All groups (except 0) shut down!")
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun restorePower() {
        CoroutineScope(Dispatchers.Main).launch {
            DatabaseHelper.connectAndExecute { conn ->
                val stmt = conn.prepareStatement("UPDATE addresses SET current_power_status = 1")
                stmt.executeUpdate()
            }
            logSystemAction("POWER RESTORED (Android)")
            logToScreen("Power restored to all groups.")
        }
    }

    private fun showGroupInputDialog() {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("Shutdown Group (0-9)")
            .setView(input)
            .setPositiveButton("Shutdown") { _, _ ->
                val groupStr = input.text.toString()
                if(groupStr.isNotEmpty()) {
                    val g = groupStr.toInt()
                    CoroutineScope(Dispatchers.Main).launch {
                        DatabaseHelper.connectAndExecute { conn ->
                            val stmt = conn.prepareStatement("UPDATE addresses SET current_power_status = 0 WHERE group_id = ?")
                            stmt.setInt(1, g)
                            stmt.executeUpdate()
                        }
                        logToScreen("Group $g shut down.")
                    }
                }
            }
            .show()
    }

    private fun fetchLogs() {
        CoroutineScope(Dispatchers.Main).launch {
            val logs = DatabaseHelper.getSystemLogs()
            tvConsole.text = logs.joinToString("\n")
        }
    }

    private fun logToScreen(msg: String) {
        tvConsole.append("\n> $msg")
    }

    private suspend fun logSystemAction(msg: String) {
        DatabaseHelper.connectAndExecute { conn ->
            val logStmt = conn.prepareStatement("INSERT INTO system_logs (message) VALUES (?)")
            logStmt.setString(1, msg)
            logStmt.executeUpdate()
        }
    }

    private fun logout() {
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}