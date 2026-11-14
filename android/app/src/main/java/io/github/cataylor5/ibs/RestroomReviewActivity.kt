package io.github.cataylor5.ibs

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.cataylor5.ibs.ui.theme.IBSTheme

class RestroomReviewActivity : ComponentActivity() {
    private val review: RestroomReviewViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            IBSTheme {
                LaunchedEffect(review.complete) {
                    if (review.complete) { setResult(Activity.RESULT_OK); finish() }
                }
                ReviewPage(review, onBack = { if (!review.saving) finish() })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewPage(review: RestroomReviewViewModel, onBack: () -> Unit) {
    val draft = review.draft
    val editable = !review.saving && !review.complete
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    Button(onClick = review::submit, enabled = editable && !review.loading && review.context != null && draft.valid,
                        modifier = Modifier.fillMaxWidth()) { Text(if (review.saving) "Saving…" else if (review.error != null) "Retry save" else "Submit review") }
                    TextButton(onClick = onBack, enabled = !review.saving, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to trip")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rate this restroom", style = MaterialTheme.typography.headlineSmall)
            Text(review.context?.name ?: review.businessName, style = MaterialTheme.typography.titleLarge)
            if (review.demo) Text("Demo review • saved to the test database only",
                style = MaterialTheme.typography.bodySmall)
            if (review.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            review.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (review.context == null) TextButton(onClick = review::load, enabled = !review.loading) { Text("Retry") }
            }
            Text("Overall rating", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { rating ->
                    FilterChip(selected = draft.overall == rating, enabled = editable,
                        onClick = { review.edit(draft.copy(overall = rating)) }, label = { Text("$rating ★") })
                }
            }
            OutlinedTextField(value = draft.comment,
                onValueChange = { if (it.length <= 4000) review.edit(draft.copy(comment = it)) },
                label = { Text("Write a review (optional)") }, minLines = 3, maxLines = 8,
                enabled = editable, modifier = Modifier.fillMaxWidth())
            review.context?.let {
                Text("Currently listed: ${it.access} • ${it.layout}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Who can use it? (optional)", style = MaterialTheme.typography.titleMedium)
            ReviewChoices(listOf("PUBLIC" to "Public access", "CUSTOMER_ONLY" to "Patrons only",
                "MEMBERSHIP_REQUIRED" to "Membership required", "NOT_SURE" to "Not sure"),
                draft.accessChoice, editable) { review.edit(draft.copy(accessChoice = it)) }
            Text("What’s the layout? (optional)", style = MaterialTheme.typography.titleMedium)
            ReviewChoices(listOf("PRIVATE" to "Private restroom", "STALLS" to "Stalls", "NOT_SURE" to "Not sure"),
                draft.privacyChoice, editable) { review.edit(draft.copy(privacyChoice = it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewChoices(choices: List<Pair<String, String>>, selected: String?, enabled: Boolean, choose: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, label) ->
            FilterChip(selected = selected == value, enabled = enabled,
                onClick = { choose(value.takeUnless { selected == value }) }, label = { Text(label) })
        }
    }
}
