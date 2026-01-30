package com.ctrldevice.ui.macros

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ctrldevice.R
import com.ctrldevice.features.agent_engine.skills.Macro
import com.ctrldevice.features.agent_engine.skills.SkillRegistry
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MacroListActivity : Activity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: MacroAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macro_list)

        recyclerView = findViewById(R.id.macroRecyclerView)
        fab = findViewById(R.id.addMacroFab)

        recyclerView.layoutManager = LinearLayoutManager(this)

        fab.setOnClickListener {
            val intent = Intent(this, MacroEditorActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val macros = SkillRegistry.getAllMacros()
        adapter = MacroAdapter(macros) { macro ->
            val intent = Intent(this, MacroEditorActivity::class.java)
            intent.putExtra("MACRO_NAME", macro.name)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }
}
