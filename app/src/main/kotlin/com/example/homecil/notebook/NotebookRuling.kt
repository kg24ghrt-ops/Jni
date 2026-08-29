package com.example.homecil.notebook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for notebook paper ruling (lines).
 */
data class RulingConfig(
    /** Display name for this ruling type */
    val name: String,
    /** Spacing between horizontal lines */
    val lineSpacing: Dp,
    /** Left margin position */
    val marginX: Dp,
    /** Color of the ruling lines */
    val lineColor: Color,
    /** Color of the margin line */
    val marginColor: Color,
    /** Whether to show the vertical margin line */
    val showMarginLine: Boolean,
    /** Whether to reserve header space at the top */
    val showHeaderSpace: Boolean,
    /** Height of the header space */
    val headerHeight: Dp,
    /** Line width for ruling lines */
    val lineWidth: Float = 0.5f,
    /** Whether to show vertical graph lines (for graph paper) */
    val showVerticalLines: Boolean = false,
    /** Spacing between vertical lines (for graph paper) */
    val verticalLineSpacing: Dp = 0.dp
)

/**
 * Predefined ruling configurations for different notebook types.
 */
object NotebookRuling {
    
    /** College ruled - Standard notebook paper with 7mm line spacing */
    val COLLEGE = RulingConfig(
        name = "College Ruled",
        lineSpacing = 7.dp,
        marginX = 25.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 25.dp,
        lineWidth = 0.5f
    )
    
    /** Wide ruled - Larger spacing for more writing space */
    val WIDE = RulingConfig(
        name = "Wide Ruled",
        lineSpacing = 8.7.dp,
        marginX = 25.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 25.dp,
        lineWidth = 0.5f
    )
    
    /** Narrow ruled - Smaller spacing for more lines */
    val NARROW = RulingConfig(
        name = "Narrow Ruled",
        lineSpacing = 4.8.dp,
        marginX = 25.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 25.dp,
        lineWidth = 0.5f
    )
    
    /** Graph paper - Grid pattern */
    val GRAPH = RulingConfig(
        name = "Graph Paper",
        lineSpacing = 6.35.dp,
        marginX = 12.7.dp,
        lineColor = Color(0xFFE0E0E0),
        marginColor = Color(0xFFE0E0E0),
        showMarginLine = false,
        showHeaderSpace = false,
        headerHeight = 0.dp,
        lineWidth = 0.3f,
        showVerticalLines = true,
        verticalLineSpacing = 6.35.dp
    )
    
    /** Legal ruled - Red lines for legal documents */
    val LEGAL = RulingConfig(
        name = "Legal Ruled",
        lineSpacing = 8.7.dp,
        marginX = 32.dp,
        lineColor = Color(0xFFE57373),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 30.dp,
        lineWidth = 0.5f
    )
    
    /** Plain paper - No ruling lines */
    val PLAIN = RulingConfig(
        name = "Plain Paper",
        lineSpacing = 0.dp,
        marginX = 0.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = false,
        showHeaderSpace = false,
        headerHeight = 0.dp,
        lineWidth = 0.5f
    )
    
    /** Dot grid - Dotted pattern for bullet journaling */
    val DOT_GRID = RulingConfig(
        name = "Dot Grid",
        lineSpacing = 7.dp,
        marginX = 0.dp,
        lineColor = Color(0xFFE0E0E0),
        marginColor = Color(0xFFE0E0E0),
        showMarginLine = false,
        showHeaderSpace = false,
        headerHeight = 0.dp,
        lineWidth = 0.0f, // Dots, not lines
        showVerticalLines = true,
        verticalLineSpacing = 7.dp
    )
    
    /** Cornelled ruled - For Cornell note-taking system */
    val CORNELL = RulingConfig(
        name = "Cornell Ruled",
        lineSpacing = 7.dp,
        marginX = 50.dp,
        lineColor = Color(0xFFA9C2D9),
        marginColor = Color(0xFFE57373),
        showMarginLine = true,
        showHeaderSpace = true,
        headerHeight = 40.dp,
        lineWidth = 0.5f
    )
    
    /** All available ruling configurations */
    val ALL = listOf(COLLEGE, WIDE, NARROW, GRAPH, LEGAL, PLAIN, DOT_GRID, CORNELL)
    
    /** Get ruling config by name */
    fun fromName(name: String): RulingConfig? {
        return ALL.find { it.name.equals(name, ignoreCase = true) }
    }
    
    /** Get default ruling config */
    fun default(): RulingConfig = COLLEGE
}
