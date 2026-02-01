package com.example.semestr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.semestr.R
import com.example.semestr.data.local.BuildingEntity

@Composable
fun BuildingCell(
    building: BuildingEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            val imageRes = when (building.type) {
                "CAMPFIRE" -> R.drawable.main0
                "TENT" -> R.drawable.main0
                else -> R.drawable.main0
            }
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = building.type,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}