package io.github.cataylor5.ibs
// This places the file in our app's existing package.

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// These imports handle starting the Android screen and displaying Compose content.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// These imports arrange items on the screen and let the page scroll.

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
// These imports provide the buttons, cards, text, and screen layout.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
// These imports let the screen remember and respond to changing values.

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.cataylor5.ibs.ui.theme.IBSTheme
// These imports handle alignment, spacing, previews, and our existing theme.


class MainActivity : ComponentActivity() {
    // This is the Android screen that opens when the app starts.

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android calls this function when it creates the screen.

        super.onCreate(savedInstanceState)
        // This runs the normal Android screen setup.

        enableEdgeToEdge()
        // This lets the app draw behind the system bars.

        setContent {
            // This starts the Compose interface.

            IBSTheme {
                // This applies our existing app colors and text styles.

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // This provides the screen layout and system-bar spacing.

                    IBSHomeScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                    // This displays our home screen within that spacing.
                }
            }
        }
    }
}


@Composable
fun IBSHomeScreen(modifier: Modifier = Modifier) {
    // This function describes what appears on the home screen.

    var showRestrooms by rememberSaveable { mutableStateOf(false) }
    // This starts with the sample list hidden and remembers the selection.

    Column(
        modifier = modifier
            .fillMaxSize()
            // This uses the available screen space.

            .verticalScroll(rememberScrollState())
            // This lets us scroll when the content exceeds the screen height.

            .padding(24.dp),
        // This adds space around the page's contents.

        verticalArrangement = Arrangement.spacedBy(16.dp)
        // This adds consistent spacing between the items.
    ) {
        Text(
            text = "IBS",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        // This displays the app title using our theme's main color.

        Text(
            text = "A bathroom break should be part of the route.",
            style = MaterialTheme.typography.titleMedium
        )
        // This displays the introductory message.

        Text(
            text = "Demo mode: the restroom examples below are fictional.",
            style = MaterialTheme.typography.bodyMedium
        )
        // This makes it clear that we are displaying practice data.

        Button(
            onClick = { showRestrooms = !showRestrooms },
            // Each tap switches the sample list between visible and hidden.

            modifier = Modifier.fillMaxWidth()
            // This makes the button fill the available width.
        ) {
            Text(
                text = if (showRestrooms) {
                    "Hide sample restrooms"
                } else {
                    "Find a Restroom"
                }
            )
            // This changes the button's wording to match its next action.
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            // This creates a container for the future map.

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // This centers the placeholder text inside the map area.

                Text(
                    text = "Map preview\nGoogle Maps will be added here.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (showRestrooms) {
            // The following items appear only when the list is visible.

            Text(
                text = "Sample restrooms",
                style = MaterialTheme.typography.titleLarge
            )

            RestroomCard(
                name = "Demo Library",
                accessDescription = "Public access • No fee"
            )
            // This displays our first fictional restroom.

            RestroomCard(
                name = "Demo Café",
                accessDescription = "Customers only • Purchase required"
            )

            RestroomCard(
                name = "Demo Transit Center",
                accessDescription = "Public access • Fee required"
            )
        } else {
            Text(
                text = "Tap Find a Restroom to explore the sample list.",
                style = MaterialTheme.typography.bodyMedium
            )
            // This gives the user an instruction while the list is hidden.
        }
    }
}


@Composable
fun RestroomCard(name: String, accessDescription: String) {
    // This reusable component displays one restroom's information.

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // This spaces the information within the card.

            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium
            )
            // This displays the name passed into this card.

            Text(
                text = accessDescription,
                style = MaterialTheme.typography.bodyMedium
            )
            // This displays the access and payment information.

            Text(
                text = "No ratings yet",
                style = MaterialTheme.typography.bodySmall
            )
            // Our fictional examples do not have user ratings.
        }
    }
}


@Preview(showBackground = true)
@Composable
fun IBSHomeScreenPreview() {
    // This provides an Android Studio preview of the home screen.

    IBSTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            IBSHomeScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}