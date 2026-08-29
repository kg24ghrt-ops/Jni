package com.example.homecil.notebook

/**
 * Legacy state classes have been removed.
 *
 * All notebook state is now owned by [NotebookUiState] inside
 * [NotebookViewModel]. The viewport math lives in [NotebookViewportMath].
 *
 * Removing the duplicate data classes eliminates confusion about which
 * state holder is canonical and prevents accidental use of stale types.
 */
