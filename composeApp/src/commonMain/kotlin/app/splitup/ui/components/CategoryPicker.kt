package app.splitup.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.splitup.shared.domain.model.Category
import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.DefaultCategories

/** Searchable category chooser, sectioned by parent group like Splitwise's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    onPick: (CategoryId) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val sections = remember(query) {
        DefaultCategories.parents.mapNotNull { parent ->
            val children = DefaultCategories.childrenOf(parent.id).filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    parent.name.contains(query, ignoreCase = true)
            }
            if (children.isEmpty()) null else parent to children
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search or select a category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                sections.forEach { (parent, children) ->
                    item(key = "header-${parent.id.value}") {
                        Text(
                            parent.name,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(children, key = { it.id.value }) { category ->
                        CategoryRow(category = category, onClick = { onPick(category.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category.id)
        Spacer(Modifier.width(16.dp))
        Text(category.name, style = MaterialTheme.typography.titleSmall)
    }
}
