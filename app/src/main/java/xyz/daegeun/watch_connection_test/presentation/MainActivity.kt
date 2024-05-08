package xyz.daegeun.watch_connection_test.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import xyz.daegeun.watch_connection_test.presentation.theme.WatchConnectionTestTheme

class MainActivity :
    ComponentActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {
    private lateinit var nodeClient: NodeClient
    private var localNode: Node? = null
    private lateinit var messageClient: MessageClient
    private lateinit var dataClient: DataClient

    private var receivedMessage = mutableStateOf("none")
    private var receivedData = mutableStateOf("none")
    private var nodeName = mutableStateOf(localNode?.displayName)
    private var isNodeNearby = mutableStateOf(localNode?.isNearby)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        nodeClient = Wearable.getNodeClient(this)
        messageClient = Wearable.getMessageClient(this)
        dataClient = Wearable.getDataClient(this)

        messageClient.addListener(this)
        dataClient.addListener(this)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(
                nodeName = nodeName.value,
                isNodeNearby = isNodeNearby.value,
                receivedMessage = receivedMessage.value,
                receivedData = receivedData.value,
                sendMessageFunction = ::sendMessageClient,
                sendDataFunction = ::sendDataClient,
                nodeResetFunction = ::nodeReset,)
        }
    }

    override fun onDestroy() {
        messageClient.removeListener(this)
        dataClient.removeListener(this)
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "watch_connectivity" -> {
                val msgData = messageEvent.data.toString()
                Log.d("MainActivity::onMessageReceived","msgData: $msgData")
                receivedMessage.value = msgData
            }
            else -> return
        }
    }

    @SuppressLint("VisibleForTests")
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { dataEvent: DataEvent ->
            when (dataEvent.type) {
                DataEvent.TYPE_CHANGED -> {
                    Log.d("MainActivity::onDataChanged", "data changed")
                    dataEvent.dataItem.also { item ->
                        val itemData = item.data.toString()
                        Log.d("MainActivity::onDataChanged","itemData: $itemData")
                        receivedData.value = itemData
//                        val path = item.uri.path
//                        Log.d("MainActivity::onDataChanged", "")
//                        when {
//                            (path.equals("watch_connectivity")) -> {
//                                val itemData = item.data.toString()
//                                Log.d("MainActivity::onDataChanged","itemData: $itemData")
//                                receivedData = itemData
//                            }
//                        }
                    }
                }
                else -> return
            }
        }
    }

    private fun sendMessageClient() {
        Log.d("MainActivity::sendMessageClient","called")
        if(localNode != null){
            messageClient.sendMessage(localNode!!.id, "/watch_connectivity", "Wear OS".toByteArray())
            Log.d("MainActivity::sendMessageClient","sendMessage called")
        }else{
            Log.e("MainActivity::sendMessageClient","localNode is Null!")
        }
    }

    @SuppressLint("VisibleForTests")
    private fun sendDataClient() {
        Log.d("MainActivity::sendDataClient","called")
        val dataRequest =
            PutDataMapRequest.create("/watch_connectivity").run {
                dataMap.putString("key", "Wear OS")
                asPutDataRequest()
            }
        dataClient.putDataItem(dataRequest)
        Log.d("MainActivity::sendDataClient","putDataItem called")
    }

    private fun nodeReset() {
        Log.d("MainActivity::nodeReset","called")
        lifecycleScope.launch {
            val connectedNodes = nodeClient.connectedNodes.await()
            localNode = connectedNodes.last()
            Log.d("MainActivity::nodeReset::coroutine","localNode refreshed: $localNode")
        }
//        nodeClient.localNode.addOnSuccessListener {
//            localNode = it
//        }.addOnFailureListener {
//            localNode = null
//        }.addOnCanceledListener {
//            localNode = null
//        }
    }
}

@Composable
fun WearApp(
    nodeName: String?,
    isNodeNearby: Boolean?,
    receivedMessage: String,
    receivedData: String,
    nodeResetFunction: () -> Unit,
    sendMessageFunction: () -> Unit,
    sendDataFunction: () -> Unit,
    modifier: Modifier = Modifier
) {
    WatchConnectionTestTheme {
        Scaffold {
            ScalingLazyColumn(modifier = modifier) {
                item { Text("Node name: $nodeName") }
                item { Text("Node nearby: $isNodeNearby") }
                item {
                    Chip(
                        onClick = { nodeResetFunction() },
                        colors = ChipDefaults.chipColors(backgroundColor = Colors().primary),
                        border = ChipDefaults.chipBorder()) {
                            Text("연결상태 갱신")
                        }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item { Text(receivedMessage) }
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item { Text(receivedData) }
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item {
                    Chip(
                        onClick = { sendMessageFunction.invoke() },
                        colors = ChipDefaults.chipColors(backgroundColor = Colors().primary),
                        border = ChipDefaults.chipBorder()) {
                            Text("RPC(MessageClient) 보내기")
                        }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item {
                    Chip(
                        onClick = { sendDataFunction.invoke() },
                        colors = ChipDefaults.chipColors(backgroundColor = Colors().primary),
                        border = ChipDefaults.chipBorder()) {
                            Text("Context(DataClient) 보내기")
                        }
                }
            }
        }
    }
}

@Preview
@Composable
private fun WearAppPreview() {
    WearApp(
        nodeName = null,
        isNodeNearby = null,
        receivedMessage = "ReceivedMessage",
        receivedData = "ReceivedData",
        sendMessageFunction = {},
        sendDataFunction = {},
        nodeResetFunction = {},
    )
}
