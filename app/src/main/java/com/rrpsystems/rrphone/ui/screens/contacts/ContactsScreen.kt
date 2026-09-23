package com.rrpsystems.rrphone.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.rrpsystems.rrphone.core.contacts.Contact
import com.rrpsystems.rrphone.core.contacts.ContactsRepository
import com.rrpsystems.rrphone.ui.components.Routes
import com.rrpsystems.rrphone.ui.components.TabScaffold
import com.rrpsystems.rrphone.ui.screens.dialer.rememberPlaceCall
import com.rrpsystems.rrphone.ui.theme.Rrp

@Composable
private fun rememberAllContacts(query: String): List<Contact> {
    val remote by ContactsRepository.remote.collectAsState()
    val local by ContactsRepository.local.collectAsState()
    return remember(remote, local, query) {
        val q = query.trim().lowercase()
        (local + remote)
            .filter {
                q.isEmpty() || it.name.lowercase().contains(q) || it.number.contains(q) ||
                    it.info.lowercase().contains(q) || it.phone.contains(q) || it.mobile.contains(q)
            }
            .sortedBy { it.name.lowercase() }
    }
}

/**
 * Agenda (D-16): lista do servidor e contatos locais juntos. Tocar no ícone
 * de telefone liga; tocar no contato mostra os detalhes (e edita, se local).
 */
@Composable
fun ContactsScreen(onNavigate: (String) -> Unit, onReturnToCall: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val contacts = rememberAllContacts(query)
    val status by ContactsRepository.status.collectAsState()
    val refreshing by ContactsRepository.refreshing.collectAsState()
    val placeCall = rememberPlaceCall()

    var detail by remember { mutableStateOf<Contact?>(null) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var creating by remember { mutableStateOf(false) }

    TabScaffold(currentRoute = Routes.CONTACTS, onNavigate = onNavigate, onReturnToCall = onReturnToCall) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            IconButton(onClick = ContactsRepository::refresh) {
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Rrp.AccentBlue)
                else Icon(Icons.Default.Refresh, "Atualizar do servidor", tint = Rrp.TextSecondary)
            }
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, "Novo contato", tint = Rrp.TextSecondary)
            }
        }
        if (status.isNotEmpty()) {
            Text(status, color = Rrp.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isEmpty()) "Nenhum contato ainda.\nConfigure a URL da agenda em Ajustes ou toque em + para criar um."
                    else "Nenhum contato encontrado.",
                    color = Rrp.TextSecondary, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(contacts) { contact ->
                    ContactRow(contact, onClick = { detail = contact }, onCall = { placeCall(contact.number) })
                }
            }
        }
    }

    detail?.let { contact ->
        ContactDetailDialog(
            contact = contact,
            onCall = { number -> detail = null; placeCall(number) },
            onEdit = { detail = null; editing = contact },
            onDelete = { detail = null; ContactsRepository.removeLocal(contact) },
            onDismiss = { detail = null },
        )
    }
    if (creating) {
        ContactEditDialog(null, onSave = { ContactsRepository.addLocal(it); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { old ->
        ContactEditDialog(old, onSave = { ContactsRepository.updateLocal(old, it); editing = null }, onDismiss = { editing = null })
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Buscar contato") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Rrp.Button, unfocusedContainerColor = Rrp.Button,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = modifier
    )
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit, onCall: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(contact.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.name, color = Rrp.TextPrimary, fontSize = 16.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (contact.local) {
                    Spacer(Modifier.width(6.dp))
                    Text("local", color = Rrp.AccentTeal, fontSize = 11.sp)
                }
            }
            Text(
                listOf(contact.number, contact.info).filter { it.isNotBlank() }.joinToString(" · "),
                color = Rrp.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (contact.number.isNotBlank()) {
            IconButton(onClick = onCall) { Icon(Icons.Default.Call, "Ligar", tint = Rrp.Green) }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Rrp.Button),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().take(1).uppercase(), color = Rrp.AccentBlue, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ContactDetailDialog(
    contact: Contact,
    onCall: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rrp.Panel,
        title = { Text(contact.name) },
        text = {
            Column {
                if (contact.info.isNotBlank()) Text(contact.info, color = Rrp.TextSecondary)
                listOf("Ramal/número" to contact.number, "Telefone" to contact.phone, "Celular" to contact.mobile)
                    .filter { it.second.isNotBlank() }
                    .forEach { (label, number) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onCall(number) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, color = Rrp.TextSecondary, fontSize = 12.sp)
                                Text(number, color = Rrp.TextPrimary, fontSize = 16.sp)
                            }
                            Icon(Icons.Default.Call, "Ligar", tint = Rrp.Green)
                        }
                    }
                if (contact.email.isNotBlank()) {
                    Text("E-mail", color = Rrp.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(contact.email, color = Rrp.TextPrimary)
                }
                if (!contact.local) {
                    Text("Contato do servidor", color = Rrp.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            if (contact.local) {
                Row {
                    TextButton(onClick = { confirmDelete = true }) { Text("Excluir", color = Rrp.Red) }
                    TextButton(onClick = onEdit) { Text("Editar") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Rrp.Panel,
            title = { Text("Excluir contato") },
            text = { Text("Excluir \"${contact.name}\"?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Excluir", color = Rrp.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Mesmos campos do desktop: nome, ramal/número e observação. */
@Composable
private fun ContactEditDialog(existing: Contact?, onSave: (Contact) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var info by remember { mutableStateOf(existing?.info ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rrp.Panel,
        title = { Text(if (existing == null) "Novo contato" else "Editar contato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(
                    number, { number = it }, label = { Text("Ramal ou número") }, singleLine = true,
                    placeholder = { Text("ex.: 2130") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(info, { info = it }, label = { Text("Observação") }, singleLine = true,
                    placeholder = { Text("ex.: Financeiro") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = number.isNotBlank(),
                onClick = {
                    onSave(
                        (existing ?: Contact(name = "", number = "")).copy(
                            name = name.trim().ifEmpty { number.trim() }, number = number.trim(), info = info.trim(), local = true
                        )
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Escolha de destino a partir da agenda (usado na transferência). */
@Composable
fun ContactPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val contacts = rememberAllContacts(query).filter { it.number.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rrp.Panel,
        title = { Text("Escolher contato") },
        text = {
            Column {
                SearchField(query, { query = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(contacts) { c ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(c.number) }.padding(vertical = 10.dp)) {
                            Text(c.name, color = Rrp.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.number, color = Rrp.TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
