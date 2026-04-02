package com.example.estakada.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.estakada.AppGraph
import com.example.estakada.data.local.RegistryRowEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private sealed class Dest(val route: String, val title: String) {
    data object Splash : Dest("splash", "Estakada")
    data object Registry : Dest("registry", "Реестр")
    data object Info : Dest("info", "Сведения")
    data object Tools : Dest("tools", "Инструменты")
    data object Import : Dest("import", "Импорт CSV")
    data object Edit : Dest("edit", "Редактор")
}

private fun csvEscape(value: String): String {
    val needsQuotes = value.contains(';') || value.contains('"') || value.contains('\n') || value.contains('\r')
    if (!needsQuotes) return value
    return "\"${value.replace("\"", "\"\"")}\""
}

private fun toCsvLine(values: List<String>, delimiter: Char = ';'): String =
    values.joinToString(delimiter.toString()) { csvEscape(it) }

private fun formatShare(share: Double?): String = share?.toString() ?: ""

private fun writeCsv(context: Context, uri: Uri, rows: List<RegistryRowEntity>) {
    context.contentResolver.openOutputStream(uri, "wt")!!.use { os ->
        OutputStreamWriter(os, StandardCharsets.UTF_8).use { w ->
            w.write(
                toCsvLine(
                    listOf(
                        "id",
                        "floor",
                        "object_number",
                        "sizes",
                        "name",
                        "gender",
                        "passport",
                        "plate",
                        "phone",
                        "note"
                    )
                )
            )
            w.write("\n")

            rows.forEach { r ->
                w.write(
                    toCsvLine(
                        listOf(
                            r.sourceRowId?.toString() ?: "",
                            r.floor ?: "",
                            r.objectNumber ?: "",
                            r.sizesRaw ?: "",
                            r.ownerName ?: "",
                            formatShare(r.share),
                            r.passport ?: "",
                            r.plate ?: "",
                            r.phone ?: "",
                            r.note ?: ""
                        )
                    )
                )
                w.write("\n")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    context: Context,
    graph: AppGraph = remember { AppGraph(context.applicationContext) }
) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Проверяем наличие данных (нужно для решения куда перейти после splash)
    var hasData by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        hasData = withContext(Dispatchers.IO) { graph.db.registryDao().count() > 0 }
    }

    // Статус экспорта (показываем на экране "Инструменты")
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            exportStatus = "Экспорт..."
            try {
                val rows = withContext(Dispatchers.IO) { graph.db.registryDao().getAll() }
                withContext(Dispatchers.IO) { writeCsv(context, uri, rows) }
                exportStatus = "Готово. Экспортировано: ${rows.size}"
            } catch (t: Throwable) {
                exportStatus = "Ошибка экспорта: ${t.message}"
            } finally {
                exporting = false
            }
        }
    }

    val tabs = listOf(Dest.Registry, Dest.Info, Dest.Tools)

    Scaffold(
        topBar = {
            if (currentDestination?.route != Dest.Splash.route) {
                TopAppBar(
                    title = {
                        val title = when (currentDestination?.route) {
                            Dest.Registry.route -> Dest.Registry.title
                            Dest.Info.route -> Dest.Info.title
                            Dest.Tools.route -> Dest.Tools.title
                            Dest.Import.route -> Dest.Import.title
                            Dest.Edit.route -> Dest.Edit.title
                            "edit/{id}" -> Dest.Edit.title
                            else -> "Estakada"
                        }
                        Text(title)
                    }
                )
            }
        },
        bottomBar = {
            val showBottom = currentDestination?.route == Dest.Registry.route ||
                currentDestination?.route == Dest.Info.route ||
                currentDestination?.route == Dest.Tools.route

            if (showBottom) {
                NavigationBar {
                    tabs.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                exportStatus = null
                                navController.navigate(dest.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Dest.Registry.route) { saveState = true }
                                }
                            },
                            // Текст внизу убираем: будут только иконки
                            label = {},
                            icon = {
                                when (dest) {
                                    Dest.Registry -> Icon(Icons.Filled.List, contentDescription = "Реестр")
                                    Dest.Info -> Icon(Icons.Filled.Info, contentDescription = "Сведения")
                                    Dest.Tools -> Icon(Icons.Filled.Settings, contentDescription = "Инструменты")
                                    else -> {}
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Splash.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Splash.route) {
                val splashBlue = Color(0xFF0D47A1)

                // Анимация: по очереди (сначала "Геленджик", затем "ТЦ - ЭСТАКАДА")
                var start1 by remember { mutableStateOf(false) }
                var start2 by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    start1 = true
                    delay(200)
                    start2 = true
                }

                val alpha1 by animateFloatAsState(
                    targetValue = if (start1) 1f else 0f,
                    animationSpec = tween(durationMillis = 650),
                    label = "splashAlpha1"
                )
                val alpha2 by animateFloatAsState(
                    targetValue = if (start2) 1f else 0f,
                    animationSpec = tween(durationMillis = 650),
                    label = "splashAlpha2"
                )

                val offset1 by animateFloatAsState(
                    targetValue = if (start1) 0f else 12f,
                    animationSpec = tween(durationMillis = 650),
                    label = "splashOffset1"
                )
                val offset2 by animateFloatAsState(
                    targetValue = if (start2) 0f else 12f,
                    animationSpec = tween(durationMillis = 650),
                    label = "splashOffset2"
                )

                // Переход после паузы (и когда hasData будет известен)
                LaunchedEffect(hasData) {
                    if (hasData == null) return@LaunchedEffect
                    delay(1400)

                    val target = if (hasData == true) Dest.Registry.route else Dest.Tools.route
                    navController.navigate(target) {
                        popUpTo(Dest.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(splashBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Геленджик",
                            modifier = Modifier
                                .alpha(alpha1)
                                .padding(top = offset1.dp),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "ТЦ - ЭСТАКАДА",
                            modifier = Modifier
                                .alpha(alpha2)
                                .padding(top = offset2.dp),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            composable(Dest.Registry.route) {
                RegistryListScreen(
                    context = context,
                    db = graph.db,
                    rowsFlow = graph.db.registryDao().observeAll(),
                    onEdit = { id ->
                        exportStatus = null
                        navController.navigate("${Dest.Edit.route}/$id")
                    }
                )
            }

            composable(Dest.Tools.route) {
                ToolsScreen(
                    hasData = (hasData == true),
                    exportInProgress = exporting,
                    exportStatus = exportStatus,
                    onGoImport = {
                        exportStatus = null
                        navController.navigate(Dest.Import.route)
                    },
                    onGoRegistry = {
                        exportStatus = null
                        navController.navigate(Dest.Registry.route)
                    },
                    onExport = {
                        exportStatus = null
                        exportLauncher.launch("estakada-export.csv")
                    },
                    onClearDb = {
                        scope.launch {
                            withContext(Dispatchers.IO) { graph.db.registryDao().clearAll() }
                            hasData = false
                            exportStatus = "База очищена"
                            navController.navigate(Dest.Tools.route) {
                                popUpTo(Dest.Tools.route) { inclusive = true }
                            }
                        }
                    },
                    onAdd = {
                        exportStatus = null
                        navController.navigate(Dest.Edit.route)
                    }
                )
            }

            composable(Dest.Info.route) {
                SummaryScreen(
                    rowsFlow = graph.db.registryDao().observeAll(),
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Dest.Import.route) {
                CsvPreviewScreen(
                    context = context,
                    onImported = {
                        hasData = true
                        exportStatus = "Импорт завершён"
                        navController.navigate(Dest.Registry.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Dest.Edit.route) {
                RegistryEditScreen(
                    context = context,
                    db = graph.db,
                    stableId = null,
                    onDone = { navController.popBackStack() }
                )
            }

            composable("${Dest.Edit.route}/{id}") { entry ->
                RegistryEditScreen(
                    context = context,
                    db = graph.db,
                    stableId = entry.arguments?.getString("id"),
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}