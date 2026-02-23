package com.example.vmmswidget.ui

import android.os.Bundle
import android.graphics.Typeface
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.example.vmmswidget.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {
    private var lastBackPressedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
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
        val targetTab = intent?.getIntExtra(EXTRA_TAB_INDEX, 0) ?: 0
        viewPager.setCurrentItem(targetTab, false)
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

    companion object {
        const val EXTRA_TAB_INDEX = "extra_tab_index"
        private const val BACK_PRESS_INTERVAL_MS = 2000L
    }
}
