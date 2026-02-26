package com.example.vmmswidget.ui

import android.os.Bundle
import android.graphics.Typeface
import android.widget.Toast
import android.widget.TextView
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.example.vmmswidget.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {
    private var lastBackPressedAt: Long = 0L
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.view_pager)
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        viewPager.adapter = MainPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val label = when (position) {
                0 -> "📈 매출"
                1 -> "🧾 거래 내역"
                2 -> "📦 발주"
                else -> "⚙️ 설정"
            }
            tab.customView = TextView(this).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT
                setTextColor(0xFF222222.toInt())
                gravity = android.view.Gravity.CENTER
            }
        }.attach()
        applyIntentRoute(intent)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.customView as? TextView)?.setTypeface(Typeface.DEFAULT_BOLD)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                (tab.customView as? TextView)?.setTypeface(Typeface.DEFAULT)
            }

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        tabLayout.getTabAt(viewPager.currentItem)?.let { current ->
            (current.customView as? TextView)?.setTypeface(Typeface.DEFAULT_BOLD)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressedAt < BACK_PRESS_INTERVAL_MS) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    return
                }
                lastBackPressedAt = now
                Toast.makeText(this@MainActivity, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentRoute(intent)
    }

    fun consumeSalesSubtabTarget(): String? {
        val target = intent?.getStringExtra(EXTRA_SALES_SUBTAB) ?: return null
        intent?.removeExtra(EXTRA_SALES_SUBTAB)
        return target
    }

    private fun applyIntentRoute(routeIntent: Intent?) {
        val targetTab = routeIntent?.getIntExtra(EXTRA_TAB_INDEX, viewPager.currentItem) ?: viewPager.currentItem
        viewPager.setCurrentItem(targetTab, false)
    }

    companion object {
        const val EXTRA_TAB_INDEX = "extra_tab_index"
        const val EXTRA_SALES_SUBTAB = "extra_sales_subtab"
        const val SALES_SUBTAB_VMMS = "vmms"
        const val SALES_SUBTAB_EASYSHOP = "easyshop"
        private const val BACK_PRESS_INTERVAL_MS = 2000L
    }
}
