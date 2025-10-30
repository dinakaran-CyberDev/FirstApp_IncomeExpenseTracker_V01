package com.firstapp.tracker

import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Sheet(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val categories: MutableList<Category> = mutableListOf(),
    val transactions: MutableList<Txn> = mutableListOf()
)

@Serializable data class Category(val id: String = UUID.randomUUID().toString(), var name: String)
@Serializable data class Txn(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String?,
    val description: String,
    val amount: Double,
    val isIncome: Boolean,
    val dateMillis: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val ctx = LocalContext.current
                val repo = remember { SheetsRepository(ctx) }
                val sheetsState = remember { mutableStateOf<List<Sheet>>(repo.loadAllSheets()) }

                Scaffold(topBar = { TopAppBar(title = { Text("FirstApp_Tracker_v01") }) }) { padding ->
                    AppScreen(ctx, sheetsState.value, repo, padding)
                }
            }
        }
    }
}

@Composable
fun AppScreen(ctx: Context, sheets: List<Sheet>, repo: SheetsRepository, padding: androidx.compose.ui.unit.PaddingValues) {
    var selectedSheet by remember { mutableStateOf<Sheet?>(sheets.firstOrNull()) }
    var showAddSheet by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.width(240.dp).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sheets", fontWeight = FontWeight.Bold)
                Button(onClick = { showAddSheet = true }) { Text("+") }
            }
            LazyColumn {
                items(sheets) { s ->
                    Card(Modifier.fillMaxWidth().padding(4.dp).clickable { selectedSheet = s }) {
                        Column(Modifier.padding(8.dp)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text("${s.transactions.size} txns", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).padding(8.dp)) {
            selectedSheet?.let {
                SheetDetail(ctx, it, repo) { updated ->
                    repo.saveSheet(updated)
                }
            } ?: Text("Select or create a sheet")
        }
    }

    if (showAddSheet) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddSheet = false },
            title = { Text("Create Sheet") },
            text = { TextField(value = name, onValueChange = { name = it }, placeholder = { Text("Sheet name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        val s = Sheet(name = name.trim())
                        repo.saveSheet(s)
                        showAddSheet = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddSheet = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SheetDetail(ctx: Context, sheet: Sheet, repo: SheetsRepository, onUpdate: (Sheet) -> Unit) {
    var addingTxn by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sheet.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row {
                Button(onClick = { addingTxn = true }) { Text("Add Txn") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { exportCsv(ctx, sheet) }) { Text("Export") }
            }
        }

        Spacer(Modifier.height(8.dp))
        val total = sheet.transactions.sumOf { if (it.isIncome) it.amount else -it.amount }
        Text("Net: 鈧�${String.format(Locale.US, "%.2f", total)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        MonthlyReport(sheet)
        YearlyReport(sheet)

        LazyColumn(Modifier.weight(1f)) {
            items(sheet.transactions) { t ->
                Card(Modifier.fillMaxWidth().padding(4.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.description)
                            val cat = sheet.categories.find { it.id == t.categoryId }?.name ?: "-"
                            Text(cat, color = Color.Gray, fontSize = 12.sp)
                            Text(SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(t.dateMillis)), fontSize = 12.sp)
                        }
                        Text(
                            (if (t.isIncome) "+" else "-") + "鈧�${t.amount}",
                            color = if (t.isIncome) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (addingTxn) AddTxnDialog(sheet, onUpdate = {
            onUpdate(it)
            addingTxn = false
        }) { addingTxn = false }
    }
}

@Composable
fun MonthlyReport(sheet: Sheet) {
    val grouped = sheet.transactions.groupBy {
        val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}"
    }
    Column(Modifier.padding(8.dp)) {
        Text("Monthly Report", fontWeight = FontWeight.Bold)
        grouped.forEach { (month, txns) ->
            val income = txns.filter { it.isIncome }.sumOf { it.amount }
            val exp = txns.filter { !it.isIncome }.sumOf { it.amount }
            Text("$month: Income 鈧�$income | Expense 鈧�$exp | Net 鈧�${income - exp}")
        }
    }
}

@Composable
fun YearlyReport(sheet: Sheet) {
    val grouped = sheet.transactions.groupBy {
        val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
        cal.get(Calendar.YEAR)
    }
    Column(Modifier.padding(8.dp)) {
        Text("Yearly Report", fontWeight = FontWeight.Bold)
        grouped.forEach { (year, txns) ->
            val income = txns.filter { it.isIncome }.sumOf { it.amount }
            val exp = txns.filter { !it.isIncome }.sumOf { it.amount }
            Text("$year: Income 鈧�$income | Expense 鈧�$exp | Net 鈧�${income - exp}")
        }
    }
}

@Composable
fun AddTxnDialog(sheet: Sheet, onUpdate: (Sheet) -> Unit, onDismiss: () -> Unit) {
    var desc by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(true) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Transaction") },
        text = {
            Column {
                TextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                TextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isIncome, onCheckedChange = { isIncome = it })
                    Text("Income (uncheck = expense)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = amt.toDoubleOrNull()
                if (desc.isNotBlank() && v != null) {
                    sheet.transactions.add(Txn(null, desc, v, isIncome))
                    onUpdate(sheet)
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun exportCsv(ctx: Context, sheet: Sheet) {
    try {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        val file = File(dir, "${sheet.name}_export.csv")
        val writer = FileWriter(file)
        writer.append("Date,Description,Amount,Type\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sheet.transactions.forEach {
            writer.append("${sdf.format(Date(it.dateMillis))},${it.description},${it.amount},${if (it.isIncome) "Income" else "Expense"}\n")
        }
        writer.flush(); writer.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

class SheetsRepository(private val ctx: Context) {
    private val json = Json { prettyPrint = true }
    private val dir = File(ctx.filesDir, "sheets").apply { if (!exists()) mkdirs() }

    fun loadAllSheets(): List<Sheet> {
        val files = dir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        val out = mutableListOf<Sheet>()
        files.forEach { f ->
            try {
                val text = f.readText()
                val s = json.decodeFromString<Sheet>(text)
                out.add(s)
            } catch (e: Exception) { }
        }
        return out
    }

    fun saveSheet(s: Sheet) {
        val f = File(dir, "sheet_${s.id}.json")
        f.writeText(json.encodeToString(s))
    }
}
