package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import com.example.data.SubscriberEntity
import com.example.data.SubscriberCycleFilter
import com.example.data.SubscriberSortOrder
import com.example.ui.FieldReadingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribersScreen(
    viewModel: FieldReadingViewModel,
    onNavigateToDetails: (String) -> Unit,
    onBack: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredSubs by viewModel.filteredSubscribers.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val subscribers by viewModel.subscribers.collectAsState()
    val unreadCount by viewModel.unreadSubscribersCount.collectAsState()
    val readCount by viewModel.readSubscribersCount.collectAsState()
    val currentCycleRange by viewModel.currentCycleRange.collectAsState()
    val cycleFilter by viewModel.cycleFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
            fusedLocation.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    viewModel.updateUserLocation(loc.latitude, loc.longitude)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "قائمة المشتركين",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshSubscribers() },
                        enabled = !isSyncing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "سحب البيانات",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("بحث باسم المشترك، الرقم المرجعي، أو الهاتف...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "بحث")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("subscriber_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Cycle & Count Info Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إجمالي المشتركين: ${subscribers.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentCycleRange.shortRangeText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Filter Chips Row (All, Unread this cycle, Read this cycle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All Chip
                FilterChip(
                    selected = cycleFilter == SubscriberCycleFilter.ALL,
                    onClick = { viewModel.setCycleFilter(SubscriberCycleFilter.ALL) },
                    label = {
                        Text("الكل (${subscribers.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Unread in Cycle Chip
                FilterChip(
                    selected = cycleFilter == SubscriberCycleFilter.UNREAD_THIS_CYCLE,
                    onClick = { viewModel.setCycleFilter(SubscriberCycleFilter.UNREAD_THIS_CYCLE) },
                    label = {
                        Text(
                            "لم تُسجل ($unreadCount)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEA580C),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier.weight(1.2f)
                )

                // Read in Cycle Chip
                FilterChip(
                    selected = cycleFilter == SubscriberCycleFilter.READ_THIS_CYCLE,
                    onClick = { viewModel.setCycleFilter(SubscriberCycleFilter.READ_THIS_CYCLE) },
                    label = {
                        Text("تمت ($readCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF16A34A),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Sorting Selection Bar (حسب الأقرب GPS / الترتيب السابق / الافتراضي)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ترتيب القائمة:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SubscriberSortOrder.values().forEach { order ->
                    val isSelected = sortOrder == order
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSortOrder(order) },
                        label = {
                            Text(
                                order.titleAr,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            when (order) {
                                SubscriberSortOrder.DEFAULT -> Icon(Icons.Default.SortByAlpha, contentDescription = null, modifier = Modifier.size(14.dp))
                                SubscriberSortOrder.PREVIOUS_INPUT_ORDER -> Icon(Icons.Default.HistoryToggleOff, contentDescription = null, modifier = Modifier.size(14.dp))
                                SubscriberSortOrder.NEAREST_LOCATION -> Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (filteredSubs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (cycleFilter == SubscriberCycleFilter.UNREAD_THIS_CYCLE && unreadCount == 0) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "رائع جداً! 🎉",
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "تم تسجيل قراءات جميع المشتركين لهذه الدورة بنجاح.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "لا يوجد مشتركين مطابقين للبحث",
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSubs, key = { it.id }) { subscriber ->
                        val isReadInCycle = viewModel.isSubscriberReadInCurrentCycle(subscriber)
                        SubscriberCard(
                            subscriber = subscriber,
                            isReadInCycle = isReadInCycle,
                            onClick = {
                                viewModel.selectSubscriber(subscriber.id)
                                onNavigateToDetails(subscriber.id)
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriberCard(
    subscriber: SubscriberEntity,
    isReadInCycle: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subscriber_card_${subscriber.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (!isReadInCycle) Color(0xFFFFEDD5) else Color(0xFFF1F5F9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subscriber.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Account Balance indicator in Shekel
                val balanceColor = if (subscriber.balance > 0) {
                    MaterialTheme.colorScheme.error // Debit balance (owed)
                } else if (subscriber.balance < 0) {
                    MaterialTheme.colorScheme.primary // Credit balance (prepaid/deposit)
                } else {
                    MaterialTheme.colorScheme.outline
                }

                Surface(
                    color = balanceColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "الحساب: ${Math.abs(subscriber.balance)} شيكل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cycle Reading Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReadInCycle) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "قراءة الدورة مسجلة",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "بانتظار قراءة الدورة",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }

                Text(
                    text = "الرقم المرجعي: ${subscriber.refNumber}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الرقم المرجعي: ${subscriber.refNumber}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
                Text(
                    text = "الهاتف: ${subscriber.phone}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = subscriber.address,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "آخر قراءة: ${subscriber.lastReading} ك.و",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "تاريخها: ${subscriber.lastReadingDate}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}
