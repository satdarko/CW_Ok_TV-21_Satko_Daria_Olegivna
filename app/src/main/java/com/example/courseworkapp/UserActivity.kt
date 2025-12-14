package com.example.courseworkapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class UserAddress(
    val id: Int,
    val name: String,
    val groupId: Int,
    val hasPower: Boolean,
    val genStatus: String,
    val genAuto: Boolean,
    val isManual: Boolean
)

class UserActivity : AppCompatActivity() {
    private lateinit var login: String
    private lateinit var tvInfo: TextView

    private var myAddresses = mutableListOf<UserAddress>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        val btnMyAddresses = findViewById<Button>(R.id.btnMyAddresses)
        val btnGenControl = findViewById<Button>(R.id.btnGeneratorControl)
        val btnSchedule = findViewById<Button>(R.id.btnViewSchedule)
        val btnReport = findViewById<Button>(R.id.btnReportFailure)
        val btnLogs = findViewById<Button>(R.id.btnSystemLogs)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        tvInfo = findViewById(R.id.tvInfoLog)
        login = intent.getStringExtra("LOGIN") ?: ""

        loadUserStatus()

        btnMyAddresses.setOnClickListener { loadUserStatus() }

        btnGenControl.setOnClickListener {
            showAddressSelectionDialog("Generator Control") { addr -> showGeneratorControlDialog(addr) }
        }

        btnSchedule.setOnClickListener {
            showAddressSelectionDialog("View Schedule") { addr -> showSchedule(addr) }
        }

        btnReport.setOnClickListener {
            showAddressSelectionDialog("Report Broken Generator") { addr -> reportFailure(addr) }
        }

        btnLogs.setOnClickListener { showSystemLogs() }

        btnSettings.setOnClickListener { showSettingsDialog() }

        btnLogout.setOnClickListener { logout() }
    }

    private fun loadUserStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            myAddresses.clear()
            var displayText = "=== YOUR ADDRESSES ===\n"

            DatabaseHelper.connectAndExecute { conn ->
                val uStmt = conn.prepareStatement("SELECT id FROM users WHERE login = ?")
                uStmt.setString(1, login)
                val uRs = uStmt.executeQuery()
                var userId = -1
                if(uRs.next()) userId = uRs.getInt(1)

                val q = """
                    SELECT a.id, a.physical_address, a.group_id, a.current_power_status, 
                           a.generator_status, a.generator_auto, a.is_manual_run 
                    FROM addresses a 
                    JOIN user_addresses ua ON a.id = ua.address_id 
                    WHERE ua.user_id = ?
                """
                val stmt = conn.prepareStatement(q)
                stmt.setInt(1, userId)
                val rs = stmt.executeQuery()

                while(rs.next()) {
                    val addr = UserAddress(
                        rs.getInt("id"),
                        rs.getString("physical_address"),
                        rs.getInt("group_id"),
                        rs.getBoolean("current_power_status"),
                        rs.getString("generator_status"),
                        rs.getBoolean("generator_auto"),
                        rs.getBoolean("is_manual_run")
                    )
                    myAddresses.add(addr)

                    // Логіка відображення статусу
                    var statusStr = ""
                    if (addr.genStatus == "BROKEN") {
                        statusStr = "❌ [GEN BROKEN] Call Service!"
                    } else if (addr.isManual) {
                        statusStr = "🟢 [GEN ON] (Manual Force)"
                    } else if (addr.hasPower) {
                        statusStr = "⚡ [GRID POWER] (OK)"
                    } else if (addr.genAuto) {
                        statusStr = "🟡 [GEN ON] (Auto Start)"
                    } else {
                        statusStr = "⚫ [BLACKOUT] (No Gen)"
                    }

                    displayText += "${myAddresses.size}. ${addr.name} (Grp: ${addr.groupId})\n   Status: $statusStr\n\n"
                }
            }

            if (myAddresses.isEmpty()) {
                tvInfo.text = "No addresses attached. Go to Settings -> Add Address."
            } else {
                tvInfo.text = displayText
            }
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf("➕ Add Address (Select from list)", "➖ Remove Address")
        AlertDialog.Builder(this)
            .setTitle("Manage Addresses")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddAddressSelectionDialog()
                    1 -> showRemoveAddressSelectionDialog()
                }
            }
            .show()
    }

    private fun showAddAddressSelectionDialog() {
        val loadingDialog = AlertDialog.Builder(this)
            .setMessage("Loading address list...")
            .setCancelable(false)
            .show()

        CoroutineScope(Dispatchers.Main).launch {
            val allAddressesMap = DatabaseHelper.getAllAddressesForSelection()
            loadingDialog.dismiss()

            if (allAddressesMap.isEmpty()) {
                Toast.makeText(this@UserActivity, "No addresses found in system!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val myAddressIds = myAddresses.map { it.id }.toSet()
            val availableAddresses = allAddressesMap.filter { it.value !in myAddressIds }

            if (availableAddresses.isEmpty()) {
                Toast.makeText(this@UserActivity, "You have added all available addresses!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val namesArray = availableAddresses.keys.toTypedArray()

            AlertDialog.Builder(this@UserActivity)
                .setTitle("Select Address to Add")
                .setItems(namesArray) { _, which ->
                    val selectedName = namesArray[which]
                    val selectedId = availableAddresses[selectedName] ?: return@setItems
                    manageAddress(selectedId, isAdding = true)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showRemoveAddressSelectionDialog() {
        if (myAddresses.isEmpty()) {
            Toast.makeText(this, "You have no addresses to remove.", Toast.LENGTH_SHORT).show()
            return
        }

        val namesArray = myAddresses.map { it.name }.toTypedArray()
        val idsArray = myAddresses.map { it.id }

        AlertDialog.Builder(this)
            .setTitle("Select Address to Remove")
            .setItems(namesArray) { _, which ->
                val selectedId = idsArray[which]
                manageAddress(selectedId, isAdding = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun manageAddress(addressId: Int, isAdding: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            val success = if (isAdding) {
                DatabaseHelper.addUserAddress(login, addressId)
            } else {
                DatabaseHelper.removeUserAddress(login, addressId)
            }

            if (success) {
                val msg = if (isAdding) "Address Added!" else "Address Removed!"
                Toast.makeText(this@UserActivity, msg, Toast.LENGTH_SHORT).show()
                loadUserStatus()
            } else {
                Toast.makeText(this@UserActivity, "Operation failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showGeneratorControlDialog(addr: UserAddress) {
        if (addr.genStatus == "NONE") {
            Toast.makeText(this, "No generator installed here.", Toast.LENGTH_SHORT).show()
            return
        }
        if (addr.genStatus == "BROKEN") {
            Toast.makeText(this, "Generator is BROKEN. Repair needed.", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            "Toggle Auto-Mode (Current: ${addr.genAuto})",
            "Toggle Manual Run (Current: ${addr.isManual})"
        )

        AlertDialog.Builder(this)
            .setTitle("Control: ${addr.name}")
            .setItems(options) { _, which ->
                CoroutineScope(Dispatchers.Main).launch {
                    when (which) {
                        0 -> {
                            DatabaseHelper.toggleGeneratorAuto(addr.id, addr.genAuto)
                            Toast.makeText(this@UserActivity, "Auto mode switched", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            DatabaseHelper.toggleGeneratorManual(addr.id, addr.isManual)
                            Toast.makeText(this@UserActivity, "Manual run switched", Toast.LENGTH_SHORT).show()
                        }
                    }
                    loadUserStatus()
                }
            }
            .show()
    }

    private fun showSchedule(addr: UserAddress) {
        CoroutineScope(Dispatchers.Main).launch {
            val schedules = DatabaseHelper.getScheduleForGroup(addr.groupId)
            val sb = StringBuilder()
            sb.append("Schedule for Group ${addr.groupId}:\n\n")
            if (schedules.isEmpty()) {
                sb.append("No schedule data available.")
            } else {
                schedules.forEach { sb.append(it).append("\n") }
            }
            tvInfo.text = sb.toString()
        }
    }

    private fun reportFailure(addr: UserAddress) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Report")
            .setMessage("Are you sure generator at ${addr.name} is BROKEN?")
            .setPositiveButton("Yes") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    DatabaseHelper.updateGeneratorStatus(addr.id, "BROKEN")

                    DatabaseHelper.connectAndExecute { conn ->
                        val stmt = conn.prepareStatement("INSERT INTO system_logs (message) VALUES (?)")
                        stmt.setString(1, "USER REPORT: Failure at ${addr.name} by $login")
                        stmt.executeUpdate()
                    }

                    Toast.makeText(this@UserActivity, "Reported! Service notified.", Toast.LENGTH_SHORT).show()
                    loadUserStatus()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showSystemLogs() {
        CoroutineScope(Dispatchers.Main).launch {
            val logs = DatabaseHelper.getSystemLogs()
            tvInfo.text = "--- SYSTEM NEWS ---\n\n" + logs.joinToString("\n\n")
        }
    }

    private fun showAddressSelectionDialog(title: String, onSelected: (UserAddress) -> Unit) {
        if (myAddresses.isEmpty()) {
            Toast.makeText(this, "No addresses attached", Toast.LENGTH_SHORT).show()
            return
        }
        val names = myAddresses.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names) { _, which ->
                onSelected(myAddresses[which])
            }
            .show()
    }

    private fun logout() {
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}