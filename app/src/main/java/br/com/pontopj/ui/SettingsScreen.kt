package br.com.pontopj.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.pontopj.AppViewModel
import br.com.pontopj.data.ImportException
import br.com.pontopj.data.Privacy
import br.com.pontopj.data.ImportedSheet
import br.com.pontopj.data.SheetReader
import br.com.pontopj.data.formatMoneyInput
import br.com.pontopj.data.parseMoney
import br.com.pontopj.data.saveToDownloads
import br.com.pontopj.data.sessionsToCsv
import br.com.pontopj.data.templateCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: AppViewModel, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val context = LocalContext.current

    var rate by remember { mutableStateOf(formatMoneyInput(vm.hourlyRate)) }
    var monthGoal by remember { mutableStateOf(formatMoneyInput(vm.monthlyGoal)) }
    var dayGoal by remember {
        mutableStateOf(
            if (vm.dailyGoalHours % 1.0 == 0.0) vm.dailyGoalHours.toInt().toString()
            else vm.dailyGoalHours.toString().replace('.', ','),
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    var importSheet by remember { mutableStateOf<ImportedSheet?>(null) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val opened = withContext(Dispatchers.IO) { runCatching { SheetReader.open(context, uri) } }
                val sheet = opened.getOrNull()
                if (sheet != null) {
                    importSheet = sheet
                } else {
                    val error = opened.exceptionOrNull()
                    dataMessage = if (error is ImportException) {
                        error.message
                    } else {
                        "Não foi possível ler o arquivo. Use .xlsx ou .csv."
                    }
                }
            }
        }
    }

    ScreenColumn {
        ScreenTitle("Ajustes", "Valor da hora, metas e permissões")

        AppCard {
            SectionTitle("Seus valores")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it },
                label = { Text("Valor da hora (R$)") },
                visualTransformation = if (Privacy.hidden) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Vale para os próximos registros. O histórico mantém o valor da época.",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = monthGoal,
                onValueChange = { monthGoal = it },
                label = { Text("Meta de ganhos do mês (R$)") },
                visualTransformation = if (Privacy.hidden) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dayGoal,
                onValueChange = { dayGoal = it },
                label = { Text("Meta diária (horas)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val r = parseMoney(rate) ?: 0.0
                    val g = parseMoney(monthGoal) ?: 0.0
                    val d = parseMoney(dayGoal)
                    if (rate.isNotBlank() && parseMoney(rate) == null) {
                        message = "Valor da hora inválido"
                        messageIsError = true
                    } else if (d == null || d <= 0.0 || d > 24.0) {
                        message = "A meta diária deve ficar entre 0 e 24 horas"
                        messageIsError = true
                    } else {
                        vm.updateSettings(r, g, d)
                        message = "Alterações salvas ✓"
                        messageIsError = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
            message?.let {
                Text(
                    it,
                    color = if (messageIsError) Palette.Danger else Palette.Gold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        AppCard {
            SectionTitle("Localização")
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasPermission) "Permissão concedida. O app mostra sua posição em tempo real enquanto está aberto."
                else "Sem permissão. Sem ela o ponto é registrado, mas sem o local.",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                if (!hasPermission) {
                    Button(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                    ) { Text("Permitir", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Configurações do app", color = Palette.Gold) }
            }
        }

        AppCard {
            SectionTitle("Aparência")
            Text(
                "Escolha o tema e a cor de destaque do app.",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    RangeChip(
                        label = mode.label,
                        selected = ThemeState.mode == mode,
                        onClick = { vm.changeThemeMode(mode) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Cor de destaque", color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accents.forEachIndexed { index, option ->
                    val selected = ThemeState.accentIndex == index
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(option.color)
                            .border(2.dp, if (selected) Palette.Text else Color.Transparent, CircleShape)
                            .clickable(onClickLabel = option.name) { vm.changeAccent(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("✓", color = Palette.OnGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "Destaque atual: ${Accents[ThemeState.accentIndex.coerceIn(Accents.indices)].name}",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        AppCard {
            SectionTitle("Notificação do ponto")
            Text(
                "Enquanto o ponto estiver aberto, uma notificação fixa acompanha sua jornada. " +
                    "Com os valores ocultos (olho fechado), o ganho também some da notificação.",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            SwitchRow("Mostrar notificação", null, vm.notifyEnabled) { vm.changeNotify(it) }

            Spacer(Modifier.height(10.dp))
            Text("O que aparece nela", color = Palette.Text, fontWeight = FontWeight.SemiBold)
            val o = vm.notifOptions
            val on = vm.notifyEnabled
            SwitchRow("Hora da entrada", null, o.entry, on) { vm.changeNotifOptions(o.copy(entry = it)) }
            SwitchRow("Tempo decorrido", "cronômetro correndo", o.time, on) { vm.changeNotifOptions(o.copy(time = it)) }
            SwitchRow("Ganho em tempo real", "some com o olho fechado", o.earnings, on) {
                vm.changeNotifOptions(o.copy(earnings = it))
            }
            SwitchRow("Meta do dia", "porcentagem, tempo que falta e barra", o.goal, on) {
                vm.changeNotifOptions(o.copy(goal = it))
            }
            SwitchRow("Botão Registrar saída", null, o.button, on) { vm.changeNotifOptions(o.copy(button = it)) }

            Spacer(Modifier.height(10.dp))
            SwitchRow(
                "Avisos",
                "meta do dia batida e lembrete se esquecer a saída (12 h aberto)",
                vm.alertsEnabled,
            ) { vm.changeAlerts(it) }
        }

        AppCard {
            SectionTitle("Importar e exportar")
            Spacer(Modifier.height(6.dp))
            Text(
                "Traga suas horas de uma planilha do Excel (.xlsx) ou de um arquivo CSV. " +
                    "O app reconhece colunas como Data, Entrada e Saída e deixa você conferir antes de importar.",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
            ) { Text("Importar de Excel ou CSV", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = {
                        val file = saveToDownloads(
                            context,
                            "modelo_horas_ponto_pj.csv",
                            "text/csv",
                            templateCsv().toByteArray(Charsets.UTF_8),
                        )
                        dataMessage = if (file != null) {
                            "Modelo salvo em Downloads: ${file.name}"
                        } else {
                            "Não foi possível salvar o modelo."
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Baixar modelo", color = Palette.Gold) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val file = saveToDownloads(
                            context,
                            "registros_ponto_pj.csv",
                            "text/csv",
                            sessionsToCsv(vm.sessions).toByteArray(Charsets.UTF_8),
                        )
                        dataMessage = if (file != null) {
                            "Registros exportados em Downloads: ${file.name}"
                        } else {
                            "Não foi possível exportar."
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Exportar registros", color = Palette.Gold) }
            }
            dataMessage?.let {
                Text(
                    it,
                    color = Palette.Gold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        AppCard {
            SectionTitle("Dados")
            Spacer(Modifier.height(6.dp))
            Text(
                "Tudo fica salvo só neste celular. Desinstalar o app apaga os registros; " +
                    "use “Exportar registros” para guardar uma cópia.",
                color = Palette.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmClear = true },
                shape = RoundedCornerShape(14.dp),
            ) { Text("Apagar todos os registros", color = Palette.Danger) }
        }

        Text(
            "Ponto PJ  ·  versão 1.1",
            color = Palette.Muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    importSheet?.let { sheet ->
        ImportDialog(
            vm = vm,
            sheet = sheet,
            onDismiss = { importSheet = null },
            onDone = { text ->
                dataMessage = text
                importSheet = null
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Palette.Surface,
            titleContentColor = Palette.Text,
            textContentColor = Palette.Muted,
            title = { Text("Apagar tudo?") },
            text = { Text("Todos os registros de entrada e saída serão removidos. Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    confirmClear = false
                }) { Text("Apagar", color = Palette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = Palette.Muted) }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    sub: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) Palette.Text else Palette.Muted)
            if (sub != null) {
                Text(sub, color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnGold,
                checkedTrackColor = Palette.Gold,
            ),
        )
    }
}
