package com.github.jing332.tts_server_android.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.os.LocaleListCompat
import androidx.core.view.MenuCompat
import androidx.core.view.setPadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.github.jing332.tts_server_android.App
import com.github.jing332.tts_server_android.BuildConfig
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.app
import com.github.jing332.tts_server_android.databinding.ActivityMainBinding
import com.github.jing332.tts_server_android.databinding.NavHeaderBinding
import com.github.jing332.tts_server_android.help.AppConfig
import com.github.jing332.tts_server_android.help.ServerConfig
import com.github.jing332.tts_server_android.help.SysTtsConfig
import com.github.jing332.tts_server_android.util.*
import com.github.jing332.tts_server_android.util.FileUtils.readAllText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import java.util.*


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        const val ACTION_OPTION_ITEM_SELECTED_ID = "ACTION_OPTION_ITEM_SELECTED_ID"
        const val KEY_MENU_ITEM_ID = "KEY_MENU_ITEM_ID"

        const val ACTION_BACK_KEY_DOWN = "ACTION_BACK_KEY_DOWN"
    }

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private lateinit var navHeaderBinding: NavHeaderBinding

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        // Fragment 容器
        val hostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = hostFragment.navController

        // 关联抽屉菜单和Fragment
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_systts, R.id.nav_server, R.id.nav_settings), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener(this)

        // 设置启动页面
        val checkedId = if (AppConfig.fragmentIndex == 1) R.id.nav_server else R.id.nav_systts
        navView.setCheckedItem(checkedId)

        val navGraph = navController.navInflater.inflate(R.navigation.mobile_navigation)
        navGraph.setStartDestination(checkedId)
        navController.graph = navGraph
        navHeaderBinding = NavHeaderBinding.bind(binding.navView.getHeaderView(0))
        navHeaderBinding.apply {
            subtitle.text = BuildConfig.VERSION_NAME

            btnLangSet.clickWithThrottle {
                val followStr = getString(R.string.app_language_follow)
                val appLocales =
                    ApplicationUtils.getAppLanguages(
                        this@MainActivity,
                        R.string.app_language_follow
                    ).map { Locale.forLanguageTag(it) }

                val displayNameList =
                    mutableListOf(followStr).apply {
                        addAll(appLocales.map { it.getDisplayName(it) })
                    }
                val currentLocale = AppCompatDelegate.getApplicationLocales().get(0)
                val checkedIndex =
                    if (currentLocale == null) 0
                    else {
                        val i =
                            appLocales.indexOfFirst { it.toLanguageTag() == currentLocale.toLanguageTag() }
                        if (i == -1) 0
                        else i + 1
                    }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setSingleChoiceItems(
                        displayNameList.toTypedArray(),
                        checkedIndex
                    ) { dlg, which ->
                        val locale = if (which > 0) {
                            LocaleListCompat.create(appLocales[which - 1])
                        } else {
                            longToast(R.string.app_language_to_follow_tip_msg)
                            app.updateLocale(Locale.getDefault())
                            LocaleListCompat.getEmptyLocaleList()
                        }
                        AppCompatDelegate.setApplicationLocales(locale)

                        dlg.dismiss()
                    }
                    .show()
            }
        }

        MyTools.checkUpdate(this)
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        updateLanguageSetBtnText()

        app.updateLocale(resources.configuration.locale)
    }

    private fun updateLanguageSetBtnText() {
        AppCompatDelegate.getApplicationLocales().let { localeListCompat ->
            val locale = localeListCompat.get(0)
            navHeaderBinding.btnLangSet.text =
                if (locale == null) getString(R.string.app_language_follow)
                else locale.getDisplayName(locale)
            navHeaderBinding.btnLangSet.apply {
                contentDescription = getString(R.string.app_language_desc, text.toString())
            }
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        val handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
        if (handled) {
            invalidateOptionsMenu()
            AppConfig.fragmentIndex = when (menuItem.itemId) {
                R.id.nav_systts -> 0
                R.id.nav_server -> 1
                else -> 0
            }
        } else {
            when (menuItem.itemId) {
                R.id.nav_killBattery -> killBattery()
                R.id.nav_checkUpdate -> MyTools.checkUpdate(this, isFromUser = true)
                R.id.nav_about -> displayAboutDialog()
            }
        }

        binding.navView.parent.let { if (it is DrawerLayout) it.closeDrawer(binding.navView) }

        return handled
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val id = when (navController.currentDestination?.id) {
            R.id.nav_systts -> R.menu.menu_systts
            R.id.nav_server -> R.menu.menu_server
            else -> return false
        }
        MenuCompat.setGroupDividerEnabled(menu, true)
        menuInflater.inflate(id, menu)
        return true
    }

    @SuppressLint("RestrictedApi")
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }
        menu?.apply {
            SysTtsConfig.apply {
                when (navController.currentDestination?.id) {
                    R.id.nav_systts -> {
                        findItem(R.id.menu_isMultiVoice)?.isChecked = isMultiVoiceEnabled
                        findItem(R.id.menu_doSplit)?.isChecked = isSplitEnabled
                        findItem(R.id.menu_replace_manager)?.isChecked = isReplaceEnabled
                        findItem(R.id.menu_isInAppPlayAudio)?.isChecked = isInAppPlayAudio
                        findItem(R.id.menu_voiceMultiple)?.isChecked = isVoiceMultipleEnabled
                        findItem(R.id.menu_groupMultiple)?.isChecked = isGroupMultipleEnabled
                    }
                    R.id.nav_server -> {
                        findItem(R.id.menu_wakeLock)?.isChecked = ServerConfig.isWakeLockEnabled
                    }
                }
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        App.localBroadcast.sendBroadcast(Intent(ACTION_OPTION_ITEM_SELECTED_ID).apply {
            putExtra(KEY_MENU_ITEM_ID, item.itemId)
        })
        return super.onOptionsItemSelected(item)
    }

    private var lastBackDownTime = 0L
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            SystemClock.elapsedRealtime().let {
                if (it - lastBackDownTime <= 1500) {
                    finish()
                } else {
                    toast(getString(R.string.app_down_again_to_exit))
                    lastBackDownTime = it
                }
                return true
            }

        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @Suppress("DEPRECATION")
    private fun displayAboutDialog() {
        val tv = TextView(this)
        tv.movementMethod = LinkMovementMethod()
        tv.text = Html.fromHtml(resources.openRawResource(R.raw.abort_info).readAllText())
        tv.gravity = Gravity.CENTER /* 居中 */
        tv.setPadding(25)
        MaterialAlertDialogBuilder(this).setTitle(R.string.about).setView(tv)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun killBattery() {
        val intent = Intent()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                toast(R.string.added_background_whitelist)
            } else {
                try {
                    intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    toast(R.string.system_not_support_please_manual_set)
                    e.printStackTrace()
                }
            }
        }
    }
}