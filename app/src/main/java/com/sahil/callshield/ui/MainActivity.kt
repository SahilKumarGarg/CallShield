package com.sahil.callshield.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.content.res.ColorStateList
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.sahil.callshield.R
import com.sahil.callshield.data.AppDatabase
import com.sahil.callshield.data.BlockRuleEntity
import com.sahil.callshield.data.CallLogEntity
import com.sahil.callshield.util.BatteryOptimizationHelper
import com.sahil.callshield.util.RoleManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var tabLayout: TabLayout

    // Containers for Tabs
    private lateinit var containerDashboard: View
    private lateinit var containerRules: View
    private lateinit var containerLogs: View

    // Dashboard views
    private lateinit var tvStatusIcon: TextView
    private lateinit var tvProtectionHeader: TextView
    private lateinit var tvProtectionDesc: TextView
    private lateinit var btnEnableRole: MaterialButton

    // Battery Optimization Controls
    private lateinit var tvBatteryOptStatus: TextView
    private lateinit var btnEnableBatteryOpt: MaterialButton
    private lateinit var btnDisableBatteryOpt: MaterialButton

    // Saved Contacts Screening Controls
    private lateinit var tvContactsStatus: TextView
    private lateinit var btnEnableContacts: MaterialButton
    private lateinit var btnDisableContacts: MaterialButton

    private lateinit var tvStatRules: TextView
    private lateinit var tvStatLogs: TextView
    private lateinit var tvRulesCount: TextView
    private lateinit var btnQuickAddRule: MaterialButton
    private lateinit var btnDashboardManageRules: MaterialButton

    // Rules tab views
    private lateinit var btnAddRuleTab: MaterialButton
    private lateinit var btnExportRulesTab: MaterialButton
    private lateinit var btnImportRulesTab: MaterialButton
    private lateinit var cardEmptyRules: MaterialCardView
    private lateinit var rvRules: RecyclerView
    private lateinit var ruleAdapter: RuleAdapter

    // Logs tab views
    private lateinit var btnClearLogs: MaterialButton
    private lateinit var cardEmptyLogs: MaterialCardView
    private lateinit var rvLogs: RecyclerView
    private lateinit var logAdapter: LogAdapter

    private var hasPromptedOnStartup = false

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this@MainActivity, "Saved Contacts Screening Enabled! Android will now screen saved contacts.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this@MainActivity, "Contacts permission denied. Android will automatically bypass screening for saved contacts.", Toast.LENGTH_LONG).show()
        }
        updateUI()
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || RoleManagerHelper.isCallScreeningRoleHeld(this@MainActivity)) {
            // Re-activate if paused previously
            setProtectionPaused(false)
            Toast.makeText(this@MainActivity, "Call Shield is now actively screening calls!", Toast.LENGTH_LONG).show()
            updateUI()
        } else {
            showDirectSettingsGuideDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbarAndMenu()
        setupTabs()
        setupRecyclerViews()
        setupListeners()

        // Clean up legacy preloaded country-wide blocks (e.g. +91) so calls are never blocked erroneously
        cleanupLegacyPreloadedRules()

        // Observe rules and logs in real-time Flows
        observeData()

        // First-run automatic onboarding check
        if (!RoleManagerHelper.isCallScreeningRoleHeld(this@MainActivity) && !hasPromptedOnStartup) {
            hasPromptedOnStartup = true
            showFirstTimeSetupDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        refreshRulesNow()
    }

    private fun initViews() {
        topAppBar = findViewById(R.id.topAppBar)
        tabLayout = findViewById(R.id.tabLayout)

        // Ensure top-left toolbar logo is explicitly set with vector drawable
        val ivToolbarLogo: AppCompatImageView? = findViewById(R.id.ivToolbarLogo)
        ivToolbarLogo?.setImageResource(R.drawable.ic_callshield_logo)

        containerDashboard = findViewById(R.id.containerDashboard)
        containerRules = findViewById(R.id.containerRules)
        containerLogs = findViewById(R.id.containerLogs)

        // Dashboard
        tvStatusIcon = findViewById(R.id.tvStatusIcon)
        tvProtectionHeader = findViewById(R.id.tvProtectionHeader)
        tvProtectionDesc = findViewById(R.id.tvProtectionDesc)
        btnEnableRole = findViewById(R.id.btnEnableRole)
        
        tvBatteryOptStatus = findViewById(R.id.tvBatteryOptStatus)
        btnEnableBatteryOpt = findViewById(R.id.btnEnableBatteryOpt)
        btnDisableBatteryOpt = findViewById(R.id.btnDisableBatteryOpt)

        tvContactsStatus = findViewById(R.id.tvContactsStatus)
        btnEnableContacts = findViewById(R.id.btnEnableContacts)
        btnDisableContacts = findViewById(R.id.btnDisableContacts)

        tvStatRules = findViewById(R.id.tvStatRules)
        tvStatLogs = findViewById(R.id.tvStatLogs)
        tvRulesCount = findViewById(R.id.tvRulesCount)
        btnQuickAddRule = findViewById(R.id.btnQuickAddRule)
        btnDashboardManageRules = findViewById(R.id.btnDashboardManageRules)

        // Rules
        btnAddRuleTab = findViewById(R.id.btnAddRuleTab)
        btnExportRulesTab = findViewById(R.id.btnExportRulesTab)
        btnImportRulesTab = findViewById(R.id.btnImportRulesTab)
        cardEmptyRules = findViewById(R.id.cardEmptyRules)
        rvRules = findViewById(R.id.rvRules)

        // Logs
        btnClearLogs = findViewById(R.id.btnClearLogs)
        cardEmptyLogs = findViewById(R.id.cardEmptyLogs)
        rvLogs = findViewById(R.id.rvLogs)
    }

    private fun setupToolbarAndMenu() {
        topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_rule -> {
                    showAddRuleDialog()
                    true
                }
                R.id.action_about -> {
                    showSupportAboutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        containerDashboard.visibility = View.VISIBLE
                        containerRules.visibility = View.GONE
                        containerLogs.visibility = View.GONE
                    }
                    1 -> {
                        containerDashboard.visibility = View.GONE
                        containerRules.visibility = View.VISIBLE
                        containerLogs.visibility = View.GONE
                        refreshRulesNow()
                    }
                    2 -> {
                        containerDashboard.visibility = View.GONE
                        containerRules.visibility = View.GONE
                        containerLogs.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerViews() {
        // Rules RecyclerView
        ruleAdapter = RuleAdapter(
            onToggle = { rule, isChecked ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.ruleDao().toggleRule(rule.id, isChecked)
                    val updatedRules = db.ruleDao().getAllRulesList()
                    withContext(Dispatchers.Main) {
                        updateRulesUI(updatedRules)
                        val state = if (isChecked) "enabled" else "disabled"
                        Toast.makeText(this@MainActivity, "Rule '${rule.name}' $state", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = { rule ->
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Delete Rule")
                    .setMessage("Remove '${rule.name}' [${rule.pattern}] from active screening rules?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        dialog.dismiss()
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getInstance(applicationContext)
                            db.ruleDao().deleteRule(rule)
                            val updatedRules = db.ruleDao().getAllRulesList()
                            withContext(Dispatchers.Main) {
                                updateRulesUI(updatedRules)
                                Toast.makeText(this@MainActivity, "Rule deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        rvRules.layoutManager = LinearLayoutManager(this@MainActivity)
        rvRules.isNestedScrollingEnabled = false
        rvRules.setHasFixedSize(false)
        rvRules.adapter = ruleAdapter

        // Logs RecyclerView
        logAdapter = LogAdapter()
        rvLogs.layoutManager = LinearLayoutManager(this@MainActivity)
        rvLogs.isNestedScrollingEnabled = false
        rvLogs.setHasFixedSize(false)
        rvLogs.adapter = logAdapter
    }

    private fun setupListeners() {
        btnEnableRole.setOnClickListener {
            handleActivationClick()
        }

        btnEnableBatteryOpt.setOnClickListener {
            handleEnableBatteryOptimization()
        }

        btnDisableBatteryOpt.setOnClickListener {
            handleDisableBatteryOptimization()
        }

        btnEnableContacts.setOnClickListener {
            handleEnableContactsScreening()
        }

        btnDisableContacts.setOnClickListener {
            handleDisableContactsScreening()
        }

        btnQuickAddRule.setOnClickListener {
            showAddRuleDialog()
        }

        btnAddRuleTab.setOnClickListener {
            showAddRuleDialog()
        }

        btnDashboardManageRules.setOnClickListener {
            tabLayout.getTabAt(1)?.select()
        }

        btnExportRulesTab.setOnClickListener {
            showExportRulesDialog()
        }

        btnImportRulesTab.setOnClickListener {
            showImportRulesDialog()
        }

        btnClearLogs.setOnClickListener {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Clear Screening Log")
                .setMessage("Are you sure you want to clear all call screening history? This cannot be undone.")
                .setPositiveButton("Clear All") { dialog, _ ->
                    dialog.dismiss()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(applicationContext)
                        db.logDao().clearLogs()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Activity log cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateRulesUI(rules: List<BlockRuleEntity>) {
        val activeCount = rules.count { it.enabled }
        tvStatRules.text = activeCount.toString()
        tvRulesCount.text = getString(R.string.status_rules_count, activeCount)
        cardEmptyRules.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

        // Submit fresh ArrayList to force AsyncListDiffer to re-diff and dispatch UI changes
        ruleAdapter.submitList(ArrayList(rules)) {
            rvRules.post {
                rvRules.requestLayout()
                containerRules.requestLayout()
            }
        }
    }

    private fun refreshRulesNow(scrollToTop: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val rules = db.ruleDao().getAllRulesList()
            withContext(Dispatchers.Main) {
                updateRulesUI(rules)
                if (scrollToTop) {
                    rvRules.post {
                        rvRules.scrollToPosition(0)
                        containerRules.scrollTo(0, 0)
                    }
                }
            }
        }
    }

    private fun updateLogsUI(logs: List<CallLogEntity>) {
        tvStatLogs.text = logs.size.toString()
        cardEmptyLogs.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        logAdapter.submitList(ArrayList(logs)) {
            rvLogs.post {
                rvLogs.requestLayout()
                containerLogs.requestLayout()
            }
        }
    }

    private fun observeData() {
        val db = AppDatabase.getInstance(applicationContext)

        // Observe rules in real-time
        lifecycleScope.launch {
            db.ruleDao().getAllRules().collect { rules ->
                updateRulesUI(rules)
            }
        }

        // Observe call screening logs in real-time
        lifecycleScope.launch {
            db.logDao().getRecentLogs().collect { logs ->
                updateLogsUI(logs)
            }
        }
    }

    private fun isProtectionPaused(): Boolean {
        val prefs = getSharedPreferences("call_shield_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_protection_paused", false)
    }

    private fun setProtectionPaused(paused: Boolean) {
        val prefs = getSharedPreferences("call_shield_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("is_protection_paused", paused) }
        updateUI()
    }

    private fun isContactsScreeningEnabled(): Boolean {
        val prefs = getSharedPreferences("call_shield_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("screen_contacts_enabled", true)
    }

    private fun setContactsScreeningEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("call_shield_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("screen_contacts_enabled", enabled) }
        updateUI()
    }

    private fun isBatteryOptimizationOverrideDisabled(): Boolean {
        val prefs = getSharedPreferences("call_shield_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("battery_optimization_override_disabled", false)
    }

    private fun setBatteryOptimizationOverrideDisabled(disabled: Boolean) {
        val prefs = getSharedPreferences("call_shield_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("battery_optimization_override_disabled", disabled) }
        updateUI()
    }

    private fun updateUI() {
        val isHeld = RoleManagerHelper.isCallScreeningRoleHeld(this@MainActivity)
        val isPaused = isProtectionPaused()

        if (isHeld) {
            if (isPaused) {
                tvStatusIcon.text = "⏸️"
                tvProtectionHeader.setText(R.string.status_protection_paused)
                tvProtectionHeader.setTextColor("#F59E0B".toColorInt())
                tvProtectionDesc.setText(R.string.status_protection_paused_desc)
                btnEnableRole.setText(R.string.btn_protection_paused)
                btnEnableRole.backgroundTintList = ColorStateList.valueOf("#B45309".toColorInt())
            } else {
                tvStatusIcon.text = "🛡️"
                tvProtectionHeader.setText(R.string.status_protection_active)
                tvProtectionHeader.setTextColor("#10B981".toColorInt())
                tvProtectionDesc.setText(R.string.status_protection_active_desc)
                btnEnableRole.setText(R.string.btn_protection_active)
                btnEnableRole.backgroundTintList = ColorStateList.valueOf("#065F46".toColorInt())
            }
        } else {
            tvStatusIcon.text = "⚠️"
            tvProtectionHeader.setText(R.string.status_protection_inactive)
            tvProtectionHeader.setTextColor("#EF4444".toColorInt())
            tvProtectionDesc.setText(R.string.status_protection_inactive_desc)
            btnEnableRole.setText(R.string.btn_protection_inactive)
            btnEnableRole.backgroundTintList = ColorStateList.valueOf("#2563EB".toColorInt())
        }

        // 1. Battery Optimization Status and Buttons
        val isBatteryIgnored = BatteryOptimizationHelper.isBatteryOptimizationIgnored(this@MainActivity)
        val isBatteryDisabled = isBatteryOptimizationOverrideDisabled()
        val isBatteryEffectivelyExempt = isBatteryIgnored && !isBatteryDisabled

        val uniformStrokePx = (1 * resources.displayMetrics.density).toInt()

        if (isBatteryEffectivelyExempt) {
            tvBatteryOptStatus.setText(R.string.status_exempted)
            tvBatteryOptStatus.setTextColor("#10B981".toColorInt())
            tvBatteryOptStatus.setBackgroundColor("#064E3B".toColorInt())

            // Enable button highlighted as active
            btnEnableBatteryOpt.setText(R.string.status_exempted)
            btnEnableBatteryOpt.backgroundTintList = ColorStateList.valueOf("#059669".toColorInt())
            btnEnableBatteryOpt.setTextColor("#FFFFFF".toColorInt())
            btnEnableBatteryOpt.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnEnableBatteryOpt.strokeWidth = uniformStrokePx

            // Disable button ready to turn off exemption
            btnDisableBatteryOpt.setText(R.string.btn_disable_exemption)
            btnDisableBatteryOpt.backgroundTintList = ColorStateList.valueOf("#1E293B".toColorInt())
            btnDisableBatteryOpt.setTextColor("#EF4444".toColorInt())
            btnDisableBatteryOpt.strokeColor = ColorStateList.valueOf("#EF4444".toColorInt())
            btnDisableBatteryOpt.strokeWidth = uniformStrokePx
        } else {
            if (isBatteryIgnored && isBatteryDisabled) {
                tvBatteryOptStatus.setText(R.string.status_disabled_in_app)
            } else {
                tvBatteryOptStatus.setText(R.string.status_restricted)
            }
            tvBatteryOptStatus.setTextColor("#F59E0B".toColorInt())
            tvBatteryOptStatus.setBackgroundColor("#451A03".toColorInt())

            btnEnableBatteryOpt.setText(R.string.btn_enable_exemption)
            btnEnableBatteryOpt.backgroundTintList = ColorStateList.valueOf("#2563EB".toColorInt())
            btnEnableBatteryOpt.setTextColor("#FFFFFF".toColorInt())
            btnEnableBatteryOpt.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnEnableBatteryOpt.strokeWidth = uniformStrokePx

            btnDisableBatteryOpt.setText(R.string.btn_disabled)
            btnDisableBatteryOpt.backgroundTintList = ColorStateList.valueOf("#334155".toColorInt())
            btnDisableBatteryOpt.setTextColor("#94A3B8".toColorInt())
            btnDisableBatteryOpt.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnDisableBatteryOpt.strokeWidth = uniformStrokePx
        }

        // 2. Saved Contacts Screening Status and Buttons
        val hasContactsPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val isContactsEnabled = isContactsScreeningEnabled()

        if (hasContactsPermission && isContactsEnabled) {
            tvContactsStatus.setText(R.string.status_screening_on)
            tvContactsStatus.setTextColor("#10B981".toColorInt())
            tvContactsStatus.setBackgroundColor("#064E3B".toColorInt())

            btnEnableContacts.setText(R.string.btn_enabled)
            btnEnableContacts.backgroundTintList = ColorStateList.valueOf("#059669".toColorInt())
            btnEnableContacts.setTextColor("#FFFFFF".toColorInt())
            btnEnableContacts.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnEnableContacts.strokeWidth = uniformStrokePx

            btnDisableContacts.setText(R.string.btn_disable_bypass)
            btnDisableContacts.backgroundTintList = ColorStateList.valueOf("#1E293B".toColorInt())
            btnDisableContacts.setTextColor("#EF4444".toColorInt())
            btnDisableContacts.strokeColor = ColorStateList.valueOf("#EF4444".toColorInt())
            btnDisableContacts.strokeWidth = uniformStrokePx
        } else if (hasContactsPermission && !isContactsEnabled) {
            tvContactsStatus.setText(R.string.status_bypass_off)
            tvContactsStatus.setTextColor("#94A3B8".toColorInt())
            tvContactsStatus.setBackgroundColor("#1E293B".toColorInt())

            btnEnableContacts.setText(R.string.btn_enable_screening)
            btnEnableContacts.backgroundTintList = ColorStateList.valueOf("#2563EB".toColorInt())
            btnEnableContacts.setTextColor("#FFFFFF".toColorInt())
            btnEnableContacts.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnEnableContacts.strokeWidth = uniformStrokePx

            btnDisableContacts.setText(R.string.btn_disabled_bypassing)
            btnDisableContacts.backgroundTintList = ColorStateList.valueOf("#475569".toColorInt())
            btnDisableContacts.setTextColor("#E2E8F0".toColorInt())
            btnDisableContacts.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnDisableContacts.strokeWidth = uniformStrokePx
        } else {
            tvContactsStatus.setText(R.string.status_permission_needed)
            tvContactsStatus.setTextColor("#F59E0B".toColorInt())
            tvContactsStatus.setBackgroundColor("#451A03".toColorInt())

            btnEnableContacts.setText(R.string.btn_grant_enable)
            btnEnableContacts.backgroundTintList = ColorStateList.valueOf("#2563EB".toColorInt())
            btnEnableContacts.setTextColor("#FFFFFF".toColorInt())
            btnEnableContacts.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnEnableContacts.strokeWidth = uniformStrokePx

            btnDisableContacts.setText(R.string.btn_disable_bypass)
            btnDisableContacts.backgroundTintList = ColorStateList.valueOf("#334155".toColorInt())
            btnDisableContacts.setTextColor("#94A3B8".toColorInt())
            btnDisableContacts.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            btnDisableContacts.strokeWidth = uniformStrokePx
        }
    }

    /**
     * Handles tapping on the Protection card / button.
     * Allows user to Pause, Resume, or Turn Off (open settings).
     */
    private fun handleActivationClick() {
        val isHeld = RoleManagerHelper.isCallScreeningRoleHeld(this@MainActivity)

        if (!isHeld) {
            // Not held yet: request role from Android
            triggerRoleRequest()
            return
        }

        // Role is held: offer pause/resume or open settings
        if (isProtectionPaused()) {
            setProtectionPaused(false)
            Toast.makeText(this@MainActivity, "Protection resumed! Incoming spam calls will be screened.", Toast.LENGTH_SHORT).show()
        } else {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Manage Protection")
                .setMessage("Call Shield is currently ACTIVE and screening calls before your phone rings.\n\nWhat would you like to do?")
                .setPositiveButton("Pause Protection") { dialog, _ ->
                    dialog.dismiss()
                    setProtectionPaused(true)
                    Toast.makeText(this@MainActivity, "Protection paused. Calls will ring without screening.", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Turn Off in Settings") { dialog, _ ->
                    dialog.dismiss()
                    RoleManagerHelper.openDefaultAppsOrSettings(this@MainActivity)
                }
                .setNegativeButton("Keep Active", null)
                .show()
        }
    }

    /**
     * Enables battery optimization exemption so Call Shield runs continuously in background.
     */
    private fun handleEnableBatteryOptimization() {
        setBatteryOptimizationOverrideDisabled(false)
        val isBatteryIgnored = BatteryOptimizationHelper.isBatteryOptimizationIgnored(this@MainActivity)
        if (!isBatteryIgnored) {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimization(this@MainActivity)
        } else {
            Toast.makeText(this@MainActivity, "Battery optimization exemption ENABLED.", Toast.LENGTH_SHORT).show()
        }
        updateUI()
    }

    /**
     * Disables battery optimization exemption.
     */
    private fun handleDisableBatteryOptimization() {
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Disable Battery Optimization Exemption")
            .setMessage("Do you want to disable battery optimization exemption?\n\nYou can disable it within Call Shield or open Android System Settings to revert battery usage to 'Optimized'.")
            .setPositiveButton("Disable in App") { dialog, _ ->
                dialog.dismiss()
                setBatteryOptimizationOverrideDisabled(true)
                Toast.makeText(this@MainActivity, "Battery exemption DISABLED in Call Shield.", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .setNeutralButton("Open System Settings") { dialog, _ ->
                dialog.dismiss()
                setBatteryOptimizationOverrideDisabled(true)
                BatteryOptimizationHelper.openBatterySettings(this@MainActivity)
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Enables saved contacts screening (prompts for READ_CONTACTS permission if needed).
     */
    private fun handleEnableContactsScreening() {
        val hasContactsPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPermission) {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Grant Contacts Permission")
                .setMessage(
                    "Why Android requires this permission:\n\n" +
                    "By default, the Android Operating System automatically allows ALL calls from numbers in your phonebook to ring without screening.\n\n" +
                    "To block or screen numbers saved in your contacts, Android requires the 'Contacts' permission.\n\n" +
                    "• 100% Offline (ZERO internet permission)\n" +
                    "• Private & secure on-device processing only\n\n" +
                    "Would you like to grant permission and enable saved contacts screening?"
                )
                .setPositiveButton("Grant Permission") { dialog, _ ->
                    dialog.dismiss()
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            setContactsScreeningEnabled(true)
            Toast.makeText(this@MainActivity, "Screening saved contacts ENABLED. Rules will be enforced for saved numbers.", Toast.LENGTH_SHORT).show()
            updateUI()
        }
    }

    /**
     * Disables saved contacts screening so contacts bypass screening and ring normally.
     */
    private fun handleDisableContactsScreening() {
        setContactsScreeningEnabled(false)
        Toast.makeText(this@MainActivity, "Screening saved contacts DISABLED. Saved contacts will bypass screening and ring normally.", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    /**
     * Dialog to add a custom screening rule and save it directly to the local Room database.
     */
    private fun showAddRuleDialog() {
        val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_add_rule, null)
        val etRuleName: TextInputEditText = dialogView.findViewById(R.id.etRuleName)
        val etRulePattern: TextInputEditText = dialogView.findViewById(R.id.etRulePattern)
        val rbTypeExact: RadioButton = dialogView.findViewById(R.id.rbTypeExact)
        val rbTypeWildcard: RadioButton = dialogView.findViewById(R.id.rbTypeWildcard)
        val rbSilence: RadioButton = dialogView.findViewById(R.id.rbSilence)
        val cbSkipNotification: CheckBox = dialogView.findViewById(R.id.cbSkipNotification)
        val cbSkipCallLog: CheckBox = dialogView.findViewById(R.id.cbSkipCallLog)

        MaterialAlertDialogBuilder(this@MainActivity)
            .setView(dialogView)
            .setPositiveButton("Save Rule") { dialog, _ ->
                val name = etRuleName.text?.toString()?.trim() ?: ""
                var pattern = etRulePattern.text?.toString()?.trim() ?: ""

                if (name.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please enter a rule name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (pattern.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please enter a phone prefix or pattern", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Match rule type: Prefix (Starts With) is selected by default for prefix spam blocking
                val ruleType = when {
                    rbTypeWildcard.isChecked || pattern.contains("*") || pattern.contains("?") -> "wildcard"
                    rbTypeExact.isChecked -> "exact"
                    else -> "prefix"
                }

                val action = if (rbSilence.isChecked) "silence" else "block"
                val skipNotif = cbSkipNotification.isChecked
                val skipLog = cbSkipCallLog.isChecked

                val newRule = BlockRuleEntity(
                    id = "custom-${System.currentTimeMillis()}",
                    name = name,
                    pattern = pattern,
                    type = ruleType,
                    action = action,
                    enabled = true,
                    skipNotification = skipNotif,
                    skipCallLog = skipLog,
                    countryCode = null,
                    flag = "🛡️"
                )

                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.ruleDao().insertRule(newRule)
                    val updatedRules = db.ruleDao().getAllRulesList()
                    withContext(Dispatchers.Main) {
                        updateRulesUI(updatedRules)
                        Toast.makeText(this@MainActivity, getString(R.string.rule_saved_success, name), Toast.LENGTH_SHORT).show()
                        // Switch to Rules tab and scroll to top so user sees their new rule immediately
                        tabLayout.getTabAt(1)?.select()
                        rvRules.post {
                            rvRules.scrollToPosition(0)
                            containerRules.scrollTo(0, 0)
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Redesigned Dialog displaying rich About & Support UI/UX:
     * - Buy me a Coffee button (PayPal link)
     * - UPI Scanner & Pay button (UPI link & clipboard copy)
     * - LinkedIn button (Direct LinkedIn URL)
     * - GitHub button (Direct GitHub repository URL)
     */
    private fun showSupportAboutDialog() {
        val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_about_support, null)
        val ivAboutLogo: AppCompatImageView? = dialogView.findViewById(R.id.ivAboutLogo)
        ivAboutLogo?.setImageResource(R.drawable.ic_callshield_logo)

        val btnBuyCoffee: MaterialButton = dialogView.findViewById(R.id.btnAboutBuyCoffee)
        val btnUpiScan: MaterialButton = dialogView.findViewById(R.id.btnAboutUpiScan)
        val btnLinkedin: MaterialButton = dialogView.findViewById(R.id.btnAboutLinkedin)
        val btnGithub: MaterialButton = dialogView.findViewById(R.id.btnAboutGithub)
        val tvUpiHint: TextView = dialogView.findViewById(R.id.tvUpiHint)

        val dialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        // 1. Buy Me a Coffee (PayPal)
        btnBuyCoffee.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://paypal.me/sahilkumargarg".toUri())
                startActivity(intent)
            } catch (_: Exception) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("PayPal Link", "https://paypal.me/sahilkumargarg"))
                Toast.makeText(this@MainActivity, "PayPal link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. UPI Scanner / Pay link
        btnUpiScan.setOnClickListener {
            val upiId = "sahilgarg50@oksbi"
            val upiUri = "upi://pay?pa=$upiId&pn=Sahil%20Kumar&cu=INR&tn=CallShield%20OpenSource%20Support".toUri()
            val upiIntent = Intent(Intent.ACTION_VIEW, upiUri)
            try {
                startActivity(upiIntent)
            } catch (_: Exception) {
                // If no UPI app installed, copy UPI ID to clipboard
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", upiId))
                Toast.makeText(this@MainActivity, "UPI ID copied: $upiId", Toast.LENGTH_LONG).show()
            }
        }

        // 3. LinkedIn Connection
        btnLinkedin.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://www.linkedin.com/in/sahilkumargarg/".toUri())
                startActivity(intent)
            } catch (_: Exception) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LinkedIn", "https://www.linkedin.com/in/sahilkumargarg/"))
                Toast.makeText(this@MainActivity, "LinkedIn URL copied", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. GitHub Repository
        btnGithub.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/SahilKumarGarg".toUri())
                startActivity(intent)
            } catch (_: Exception) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("GitHub", "https://github.com/SahilKumarGarg"))
                Toast.makeText(this@MainActivity, "GitHub URL copied", Toast.LENGTH_SHORT).show()
            }
        }

        tvUpiHint.setOnClickListener {
            val upiId = "sahilgarg50@oksbi"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", upiId))
            Toast.makeText(this@MainActivity, "UPI ID copied: $upiId", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showFirstTimeSetupDialog() {
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Welcome to Call Shield")
            .setMessage(
                "To block robocalls and spam before your phone rings, Android requires Call Shield to be selected as your Default 'Caller ID & Spam app'.\n\n" +
                "• 100% Offline & Private (No internet access)\n" +
                "• Zero delay call filtering (<5ms)\n\n" +
                "Tap 'Enable Now' to set Call Shield as your spam screener."
            )
            .setPositiveButton("Enable Now") { dialog, _ ->
                dialog.dismiss()
                triggerRoleRequest()
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
                updateUI()
            }
            .setCancelable(false)
            .show()
    }

    private fun triggerRoleRequest() {
        val roleIntent = RoleManagerHelper.createRequestRoleIntent(this@MainActivity)
        if (roleIntent != null) {
            roleRequestLauncher.launch(roleIntent)
        } else {
            showDirectSettingsGuideDialog()
        }
    }

    private fun showDirectSettingsGuideDialog() {
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Set Default Caller ID App")
            .setMessage(
                "Android requires manually setting Call Shield as your spam filter:\n\n" +
                "1. Tap 'Open Settings' below.\n" +
                "2. Tap 'Caller ID & spam app' (or 'Call screening').\n" +
                "3. Select 'Call Shield'.\n\n" +
                "When you return here, protection will automatically activate!"
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                RoleManagerHelper.openDefaultAppsOrSettings(this@MainActivity)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updateUI()
            }
            .show()
    }

    /**
     * Cleans up legacy preloaded country-wide blocking rules (e.g. +91, +44)
     * so normal calls from country codes are never erroneously blocked.
     */
    private fun cleanupLegacyPreloadedRules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            db.ruleDao().deleteLegacyPreloadedRules()
        }
    }

    /**
     * Exports all local blocking rules from the Room database to JSON format.
     */
    private fun showExportRulesDialog() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val rules = withContext(Dispatchers.IO) { db.ruleDao().getAllRulesList() }
            if (rules.isEmpty()) {
                Toast.makeText(this@MainActivity, "No blocking rules saved to export.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val jsonArray = JSONArray()
            for (rule in rules) {
                val obj = JSONObject().apply {
                    put("id", rule.id)
                    put("name", rule.name)
                    put("pattern", rule.pattern)
                    put("type", rule.type)
                    put("action", rule.action)
                    put("enabled", rule.enabled)
                    put("skipNotification", rule.skipNotification)
                    put("skipCallLog", rule.skipCallLog)
                    if (rule.countryCode != null) put("countryCode", rule.countryCode)
                    if (rule.flag != null) put("flag", rule.flag)
                }
                jsonArray.put(obj)
            }

            val jsonString = jsonArray.toString(2)

            val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_export_rules, null)
            val tvSummary: TextView = dialogView.findViewById(R.id.tvExportSummary)
            val etExportData: TextInputEditText = dialogView.findViewById(R.id.etExportData)
            val btnCopy: MaterialButton = dialogView.findViewById(R.id.btnCopyExportJson)
            val btnShare: MaterialButton = dialogView.findViewById(R.id.btnShareExportJson)

            tvSummary.text = getString(R.string.export_rules_summary, rules.size)
            etExportData.setText(jsonString)

            val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                .setView(dialogView)
                .setPositiveButton("Done", null)
                .create()

            btnCopy.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Call Shield Rules JSON", jsonString))
                Toast.makeText(this@MainActivity, getString(R.string.export_rules_clipboard, rules.size), Toast.LENGTH_SHORT).show()
            }

            btnShare.setOnClickListener {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Call Shield Blocking Rules Backup")
                        putExtra(Intent.EXTRA_TEXT, jsonString)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share or Save Rules JSON"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not open share sheet", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.show()
        }
    }

    /**
     * Imports blocking rules into the local Room database from JSON or text prefixes.
     */
    private fun showImportRulesDialog() {
        val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_import_rules, null)
        val etImportData: TextInputEditText = dialogView.findViewById(R.id.etImportData)
        val cbMerge: CheckBox = dialogView.findViewById(R.id.cbMergeRules)

        MaterialAlertDialogBuilder(this@MainActivity)
            .setView(dialogView)
            .setPositiveButton("Import to Database") { dialog, _ ->
                val rawInput = etImportData.text?.toString()?.trim() ?: ""
                if (rawInput.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please paste JSON or rules to import", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val merge = cbMerge.isChecked
                lifecycleScope.launch(Dispatchers.IO) {
                    val importedRules = mutableListOf<BlockRuleEntity>()

                    try {
                        // Attempt JSON Array parsing
                        val jsonArray = JSONArray(rawInput)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val name = obj.optString("name", "Imported Rule ${i + 1}")
                            val pattern = obj.optString("pattern", "")
                            if (pattern.isNotEmpty()) {
                                val type = obj.optString("type", if (pattern.contains("*") || pattern.contains("?")) "wildcard" else "prefix")
                                val action = obj.optString("action", "block")
                                val enabled = obj.optBoolean("enabled", true)
                                val skipNotif = obj.optBoolean("skipNotification", false)
                                val skipLog = obj.optBoolean("skipCallLog", true)
                                val countryCode = if (obj.has("countryCode") && !obj.isNull("countryCode")) obj.getString("countryCode") else null
                                val flag = if (obj.has("flag") && !obj.isNull("flag")) obj.getString("flag") else "🛡️"

                                importedRules.add(
                                    BlockRuleEntity(
                                        id = "custom-${System.currentTimeMillis()}-$i",
                                        name = name,
                                        pattern = pattern,
                                        type = type,
                                        action = action,
                                        enabled = enabled,
                                        skipNotification = skipNotif,
                                        skipCallLog = skipLog,
                                        countryCode = countryCode,
                                        flag = flag
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Not a JSON Array: parse as newline or comma-separated list of prefixes
                        val lines = rawInput.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
                        for ((idx, line) in lines.withIndex()) {
                            val cleanLine = line.replace("\"", "").replace("'", "").trim()
                            if (cleanLine.length >= 2) {
                                val type = if (cleanLine.contains("*") || cleanLine.contains("?")) "wildcard" else "prefix"
                                importedRules.add(
                                    BlockRuleEntity(
                                        id = "custom-${System.currentTimeMillis()}-$idx",
                                        name = "Block $cleanLine",
                                        pattern = cleanLine,
                                        type = type,
                                        action = "block",
                                        enabled = true,
                                        skipNotification = false,
                                        skipCallLog = true,
                                        countryCode = null,
                                        flag = "🛡️"
                                    )
                                )
                            }
                        }
                    }

                    if (importedRules.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "No valid rules detected in input.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val db = AppDatabase.getInstance(applicationContext)
                    if (!merge) {
                        db.ruleDao().deleteAllRules()
                    }
                    db.ruleDao().insertRules(importedRules)
                    val updatedRules = db.ruleDao().getAllRulesList()

                    withContext(Dispatchers.Main) {
                        updateRulesUI(updatedRules)
                        Toast.makeText(this@MainActivity, getString(R.string.import_rules_success, importedRules.size), Toast.LENGTH_LONG).show()
                        tabLayout.getTabAt(1)?.select()
                        rvRules.post {
                            rvRules.scrollToPosition(0)
                            containerRules.scrollTo(0, 0)
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
