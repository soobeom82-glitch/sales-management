package com.example.vmmswidget.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.vmmswidget.R

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val home = view.findViewById<LinearLayout>(R.id.settings_home)
        val child = view.findViewById<LinearLayout>(R.id.settings_child_root)
        val childContainer = view.findViewById<FrameLayout>(R.id.settings_child_container)
        val productMenu = view.findViewById<View>(R.id.settings_menu_product_mapping)
        val loginMenu = view.findViewById<View>(R.id.settings_menu_login)
        val back = view.findViewById<View>(R.id.settings_child_back)
        val childTitle = view.findViewById<TextView>(R.id.settings_child_title)

        fun showHome() {
            home.visibility = View.VISIBLE
            child.visibility = View.GONE
        }
        fun showChild(title: String, fragment: Fragment) {
            home.visibility = View.GONE
            child.visibility = View.VISIBLE
            childTitle.text = title
            childFragmentManager.beginTransaction()
                .replace(childContainer.id, fragment)
                .commit()
        }

        productMenu.setOnClickListener { showChild("상품 매핑 설정", ProductMappingFragment()) }
        loginMenu.setOnClickListener { showChild("로그인 설정", LoginFragment()) }
        back.setOnClickListener { showHome() }
        showHome()
    }
}
