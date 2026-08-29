package com.example.homecil.notebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.homecil.PaperEngine
import com.example.homecil.PaperSize

/**
 * Test activity for verifying notebook ruling functionality.
 * Displays all available ruling types to verify they render correctly.
 */
class NotebookRulingTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NotebookRulingTestScreen()
                }
            }
        }
    }
}

@Composable
fun NotebookRulingTestScreen() {
    val density = LocalDensity.current
    val paperSize = PaperSize.A5
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Notebook Ruling Test",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "Testing all ruling types with PaperEngine",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Test each ruling configuration
        NotebookRuling.ALL.forEach { config ->
            RulingPreviewCard(
                rulingConfig = config,
                paperSize = paperSize,
                density = density
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Test with native engine disabled
        Text(
            text = "Kotlin-only rendering (native disabled)",
            style = MaterialTheme.typography.titleMedium
        )
        
        PaperEngine.useNativeEngine = false
        RulingPreviewCard(
            rulingConfig = NotebookRuling.COLLEGE,
            paperSize = paperSize,
            density = density
        )
        PaperEngine.useNativeEngine = true
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Test with baked ruling lines
        Text(
            text = "With baked ruling lines (for export)",
            style = MaterialTheme.typography.titleMedium
        )
        
        val textureWithRuling = remember(density) {
            PaperEngine.generateTexture(
                paperSize = paperSize,
                density = density,
                paperColor = android.graphics.Color.parseColor("#FBF9F2"),
                rulingConfig = NotebookRuling.COLLEGE
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(android.graphics.Color.WHITE)
        ) {
            androidx.compose.foundation.Image(
                bitmap = textureWithRuling,
                contentDescription = "Paper with baked ruling lines",
                modifier = Modifier.matchParentSize()
            )
        }
        
        Text(
            text = "Above: College ruled paper with lines baked into texture",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun RulingPreviewCard(
    rulingConfig: RulingConfig,
    paperSize: PaperSize,
    density: androidx.compose.ui.unit.Density
) {
    val texture = remember(density, rulingConfig) {
        PaperEngine.generateTexture(
            paperSize = paperSize,
            density = density,
            paperColor = android.graphics.Color.parseColor("#FBF9F2")
        )
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = rulingConfig.name,
            style = MaterialTheme.typography.titleMedium
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(android.graphics.Color.WHITE)
        ) {
            androidx.compose.foundation.Image(
                bitmap = texture,
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
            
            // Overlay the ruling grid
            NotebookGrid(
                modifier = Modifier.matchParentSize(),
                lineSpacing = rulingConfig.lineSpacing,
                marginX = rulingConfig.marginX,
                layoutResult = null,
                activeLine = -1,
                showMargin = rulingConfig.showMarginLine,
                rulingConfig = rulingConfig
            )
        }
        
        Text(
            text = "Spacing: ${rulingConfig.lineSpacing.value}dp, " +
                  "Margin: ${rulingConfig.marginX.value}dp, " +
                  "Header: ${if (rulingConfig.showHeaderSpace) "Yes" else "No"}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Preview function for Android Studio
 */
@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    widthDp = 400,
    heightDp = 800
)
@Composable
fun NotebookRulingPreview() {
    MaterialTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ruling Preview",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                // Preview college ruled
                val density = LocalDensity.current
                val texture = PaperEngine.generateTexture(
                    paperSize = PaperSize.A5,
                    density = density,
                    paperColor = android.graphics.Color.parseColor("#FBF9F2")
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .background(android.graphics.Color.WHITE)
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = texture,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize()
                    )
                    
                    NotebookGrid(
                        modifier = Modifier.matchParentSize(),
                        lineSpacing = 7.dp,
                        marginX = 25.dp,
                        layoutResult = null,
                        activeLine = -1,
                        showMargin = true,
                        rulingConfig = NotebookRuling.COLLEGE
                    )
                }
            }
        }
    }
}
