package com.addy37.crazyshitadmin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun AnalyticsScreen(state: ConsoleState) {
    val a = state.analytics
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item { Eyebrow("04 / AUDIENCE INSIGHTS",color=Cyan);Spacer(Modifier.height(8.dp));Text("Analytics",style=MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp));Text("Aggregate activity. No individual user timelines.",color=Muted) }
        item { ErrorPanel(state.errors["analytics"],state::loadAnalytics) }
        if(a==null) item {if("analytics" in state.busy) InitialLoad("Loading aggregate activity") else EmptyPanel("Analytics unavailable","Refresh to try again.",Icons.Outlined.Insights)}
        else {
            item {ConsolePanel(Modifier.fillMaxWidth(),accent=true) {
                Eyebrow("ACTIVE INSTALLS")
                Metric(a.dailyUsers,"Today",large=true)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    Metric(a.weeklyUsers,"7 days",modifier=Modifier.weight(1f));Metric(a.monthlyUsers,"30 days",modifier=Modifier.weight(1f))
                }
                Text("Generated ${dateLabel(a.generatedAt)}",color=Muted,fontSize=12.sp)
            } }
            item {RankingSection("Creator rankings","Gallery interest · weekly users & opens",a.creators,expandedInitially=true,creator=true)}
            item {BoxWithConstraints {
                if(maxWidth>620.dp) Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f)) {RankingSection("Section usage","Weekly users & opens",a.sections,true)}
                    Column(Modifier.weight(1f)) {RankingSection("Source interest","Weekly users & opens",a.sources,true)}
                } else Column(verticalArrangement=Arrangement.spacedBy(22.dp)) {
                    RankingSection("Section usage","Weekly users & opens",a.sections,true)
                    RankingSection("Source interest","Weekly users & opens",a.sources,true)
                }
            } }
            item {RankingSection("App versions","Release adoption · weekly active installs",a.versions,false,adoption=true)}
            item {RankingSection("Device models","Physical devices · weekly activity",a.deviceModels.filter {realDevice(it.value)},false,device=true)}
            item {RankingSection("Manufacturers","Weekly active installs",a.deviceManufacturers,false,device=true)}
            item {RankingSection("Android versions","Weekly active installs",a.androidVersions,false,device=true)}
            if(a.monthlyUsers==0L && a.weeklyUsers==0L && a.dailyUsers==0L) item {EmptyPanel("Waiting for activity","Metrics fill in as analytics-enabled apps report activity.",Icons.Outlined.Insights)}
            item {Text("Bars compare weekly users within each group. Counts can overlap across sections, sources, and creators.",color=Muted,fontSize=13.sp)}
        }
    }
}
@Composable private fun RankingSection(title: String, detail: String, rows: List<AdminRepository.AnalyticsRow>, expandedInitially: Boolean,
    creator: Boolean=false, device: Boolean=false, adoption: Boolean=false) {
    var expanded by rememberSaveable(title) {mutableStateOf(expandedInitially)}
    var all by rememberSaveable(title) {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {expanded=!expanded}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(title,style=MaterialTheme.typography.titleLarge);Text(detail,color=Muted,fontSize=13.sp)}
            Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded) "Collapse $title" else "Expand $title",tint=Cyan)
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement=Arrangement.spacedBy(19.dp)) {
                if(rows.isEmpty()) Text("No activity reported yet.",color=Muted)
                if(adoption && rows.isNotEmpty()) AdoptionRing(rows)
                val max = rows.maxOfOrNull {it.uniqueUsers}?.coerceAtLeast(1) ?: 1
                rows.take(if(all) rows.size else if(creator) 5 else 4).forEachIndexed {index,row ->
                    var entered by remember {mutableStateOf(false)}
                    LaunchedEffect(Unit) { entered=true }
                    val progress by animateFloatAsState(if(entered) (row.uniqueUsers.toFloat()/max).coerceIn(0f,1f) else 0f,tween(300),label="ranking")
                    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            if(creator) Text(String.format("%02d",index+1),color=if(index==0) Cyan else Muted,fontSize=13.sp)
                            Text(if(device) deviceName(row.value) else if(creator) row.value else friendly(row.value),Modifier.weight(1f),fontWeight=FontWeight.Medium)
                            Text(number(row.uniqueUsers),color=Cyan,fontWeight=FontWeight.Bold)
                        }
                        Canvas(Modifier.fillMaxWidth().height(5.dp).semantics {contentDescription="${row.value}: ${row.uniqueUsers} weekly users"}) {
                            drawLine(Panel,Offset(0f,size.height/2),Offset(size.width,size.height/2),size.height,StrokeCap.Round)
                            if(progress>0f) drawLine(if(index==0) Cyan else Cyan.copy(alpha=.55f),Offset(0f,size.height/2),Offset(size.width*progress,size.height/2),size.height,StrokeCap.Round)
                        }
                        Text(if(device) "${number(row.usersToday)} today · ${number(row.eventCount)} events" else "${number(row.eventCount)} opens",color=Muted,fontSize=12.sp)
                    }
                }
                if(rows.size>(if(creator) 5 else 4)) TextButton(onClick={all=!all}) {Text(if(all) "Show less" else "Show all ${rows.size}")}
            }
        }
        HorizontalDivider(color=Muted.copy(alpha=.12f))
    }
}
@Composable private fun AdoptionRing(rows: List<AdminRepository.AnalyticsRow>) {
    val total = rows.sumOf {it.uniqueUsers}.coerceAtLeast(1)
    val top = rows.maxByOrNull {it.uniqueUsers} ?: return
    val fraction = top.uniqueUsers.toFloat()/total
    Row(Modifier.fillMaxWidth().clip(ConsoleShape).background(Panel).padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(22.dp)) {
        Canvas(Modifier.size(70.dp).semantics {contentDescription="${(fraction*100).toInt()} percent of reported version counts use ${top.value}"}) {
            drawArc(Muted.copy(alpha=.13f),-90f,360f,false,style=Stroke(7.dp.toPx(),cap=StrokeCap.Round))
            drawArc(Cyan,-90f,360f*fraction,false,style=Stroke(7.dp.toPx(),cap=StrokeCap.Round))
        }
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("${(fraction*100).toInt()}%",style=MaterialTheme.typography.headlineLarge,color=Cyan)
            Text("Most used · ${top.value}")
            Text("Share of reported version counts",color=Muted,fontSize=12.sp)
        }
    }
}
