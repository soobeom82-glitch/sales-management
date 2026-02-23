package com.example.vmmswidget.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SalesFragment()
            1 -> TransactionsFragment()
            2 -> OrderFragment()
            else -> SettingsFragment()
        }
    }
}
