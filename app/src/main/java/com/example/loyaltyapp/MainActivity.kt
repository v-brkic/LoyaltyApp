package com.example.loyaltyapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed // (ako želiš koristiti indeks)
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.delay
import com.journeyapps.barcodescanner.ScanOptions
import java.util.UUID
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn

@OptIn(ExperimentalAnimationApi::class)
class MainActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private var currentUser by mutableStateOf<FirebaseUser?>(null)
    private var points by mutableStateOf(0)
    private var userName by mutableStateOf("Gost")

    // Nedavne aktivnosti ćemo prikazivati i dohvaćati iz Firestore-a
    // Možeš ih držati u nekoj globalnoj varijabli ili učitavati svaki put na “RecentActivityScreen”
    // Za demonstraciju, radit ćemo direktno unutar ekrana.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.addAuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                fetchUserData()
            } else {
                points = 0
                userName = "Gost"
            }
        }

        checkCameraPermission()

        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        currentScreen = currentScreen,
                        onNavigate = { screen ->
                            currentScreen = screen
                        },
                        points = points
                    )
                },
                modifier = Modifier.background(Color(0xFF2D2A2A))
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    // Home Screen
                    AnimatedVisibility(
                        visible = currentScreen is Screen.Home,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        HomeScreen(
                            userName = userName,
                            points = points,
                            onCardClick = {
                                currentScreen = Screen.Card(points)
                            }
                        )
                    }
                    // Nedavna aktivnost
                    AnimatedVisibility(
                        visible = currentScreen is Screen.Activity,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        RecentActivityScreen(onBack = { currentScreen = Screen.Profile })
                    }
                    // Promjena lozinke
                    AnimatedVisibility(
                        visible = currentScreen is Screen.ChangePassword,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        ChangePasswordScreen(onBack = { currentScreen = Screen.Profile })
                    }
                    // Iskoristi kupon
                    AnimatedVisibility(
                        visible = currentScreen is Screen.RedeemCoupon,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        RedeemCouponScreen(onBack = { currentScreen = Screen.Profile })
                    }
                    // Kontakt i podrška
                    AnimatedVisibility(
                        visible = currentScreen is Screen.Support,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SupportScreen(onBack = { currentScreen = Screen.Profile })
                    }
                    // Kartica s QR kodom
                    AnimatedVisibility(
                        visible = currentScreen is Screen.Card,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (currentScreen is Screen.Card) {
                            val screen = currentScreen as Screen.Card
                            CardScreen(
                                points = screen.points,
                                onScanClick = { launchQRScanner() },
                                onBack = { currentScreen = Screen.Home }
                            )
                        }
                    }
                    // Registracija
                    AnimatedVisibility(
                        visible = currentScreen is Screen.Register,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        RegisterScreen(
                            onRegisterSuccess = { currentScreen = Screen.Profile },
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    // **Redizajnirani ekran Nagrade**
                    AnimatedVisibility(
                        visible = currentScreen is Screen.Rewards,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        // Proslijedi i bodove ako želiš prikazati koliko je ostalo do neke nagrade
                        RewardsScreen(
                            userPoints = points,
                            onBack = { currentScreen = Screen.Home },
                            onRedeem = { rewardItem ->
                                // Ovdje definiraš logiku "iskorištavanja"
                                // npr. umanji bodove, zapiši u "recentActivities" itd.
                            }
                        )
                    }
                    // Profil Screen (Login ili prikaz profila)
                    AnimatedVisibility(
                        visible = currentScreen is Screen.Profile,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (currentUser == null) {
                            // Ako nije logiran -> Login Screen
                            LoginScreen(
                                onLoginSuccess = {
                                    currentScreen = Screen.Profile
                                },
                                onRegisterClick = { currentScreen = Screen.Register },
                                onBack = { currentScreen = Screen.Home }
                            )
                        } else {
                            // Ako je logiran -> Puni profil
                            ProfileScreen(
                                user = currentUser!!,
                                userName = userName,
                                onNavigateToActivity = { currentScreen = Screen.Activity },
                                onNavigateToChangePassword = { currentScreen = Screen.ChangePassword },
                                onNavigateToSupport = { currentScreen = Screen.Support },
                                onLogout = {
                                    FirebaseAuth.getInstance().signOut()
                                    currentScreen = Screen.Home
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun fetchUserData() {
        val db = FirebaseFirestore.getInstance()
        currentUser?.let {
            db.collection("users").document(it.uid)
                .get()
                .addOnSuccessListener { document ->
                    points = document.getLong("points")?.toInt() ?: 0
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    userName = "$firstName $lastName"
                }
                .addOnFailureListener {
                    points = 0
                    userName = "Gost"
                    Toast.makeText(this, "Greška prilikom dohvaćanja podataka", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /**
     * Kad se završi skeniranje QR koda, pozvat će se ova lambda:
     * - Ako ima sadržaja, parsiramo npr. “product=Kava;points=10”
     * - Spremamo u Firestore (recentActivities)
     * - Dodajemo bodove (ako je predviđeno)
     */
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            processQRCode(result.contents)
        } else {
            Toast.makeText(this, "Skeniranje otkazano", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchQRScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Skenirajte QR kod")
        options.setCameraId(0)
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(false)
        barcodeLauncher.launch(options)
    }

    /**
     * Provjera dopuštenja za kameru
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Dopuštenje za kameru je potrebno za skeniranje QR kodova", Toast.LENGTH_SHORT).show()
            }
        }

    /**
     * Parsiramo skenirani QR kod i kreiramo zapis u `recentActivities` subkolekciji.
     * Ovdje demo format: `product=Kava;points=10`
     */
    private fun processQRCode(qrData: String) {
        Toast.makeText(this, "Skeniran QR: $qrData", Toast.LENGTH_SHORT).show()

        val user = auth.currentUser ?: return

        // Simple “parser”: očekujemo formu “product=Kava;points=10”
        var scannedProduct = "Nepoznato"
        var scannedPoints = 0

        val parts = qrData.split(";").map { it.trim() }
        for (part in parts) {
            val keyValue = part.split("=")
            if (keyValue.size == 2) {
                when (keyValue[0]) {
                    "product" -> scannedProduct = keyValue[1]
                    "points" -> scannedPoints = keyValue[1].toIntOrNull() ?: 0
                }
            }
        }

        val db = FirebaseFirestore.getInstance()

        // Ažuriraj bodove korisniku
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val currentPoints = doc.getLong("points")?.toInt() ?: 0
                val newPoints = currentPoints + scannedPoints

                // Snimi nove bodove
                db.collection("users").document(user.uid)
                    .update("points", newPoints)
                    .addOnSuccessListener {
                        // Lokalno ažuriraj varijablu points
                        points = newPoints
                    }

                // Snimi aktivnost
                val activityData = mapOf(
                    "product" to scannedProduct,
                    "points" to scannedPoints,
                    "operation" to "Kupnja proizvoda",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                db.collection("users")
                    .document(user.uid)
                    .collection("recentActivities")
                    .add(activityData)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Greška prilikom ažuriranja bodova.", Toast.LENGTH_SHORT).show()
            }
    }
}

/**
 * Navigacija
 */
sealed class Screen {
    object Home : Screen()
    data class Card(val points: Int) : Screen()
    object Rewards : Screen()
    object Profile : Screen()
    object Activity : Screen()
    object ChangePassword : Screen()
    object RedeemCoupon : Screen()
    object Support : Screen()
    object Register : Screen()
}

@Composable
fun BottomNavigationBar(currentScreen: Screen, onNavigate: (Screen) -> Unit, points: Int) {
    NavigationBar(
        containerColor = Color(0xFFFFFFFF)
    ) {
        NavigationBarItem(
            selected = currentScreen is Screen.Home,
            onClick = { onNavigate(Screen.Home) },
            icon = { Text("🏠") },
            label = { Text("Početna") }
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Card,
            onClick = { onNavigate(Screen.Card(points)) },
            icon = { Text("💳") },
            label = { Text("Kartica") }
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Rewards,
            onClick = { onNavigate(Screen.Rewards) },
            icon = { Text("🎁") },
            label = { Text("Nagrade") }
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Profile,
            onClick = { onNavigate(Screen.Profile) },
            icon = { Text("👤") },
            label = { Text("Profil") }
        )
    }
}

/**
 * 1) HOME SCREEN
 */
data class OfferData(
    val title: String,
    val imageRes: Int,
    val description: String = "Iskoristite sjajnu ponudu i uštedite na omiljenim proizvodima."
)

@Composable
fun HomeScreen(
    userName: String,
    points: Int,
    onCardClick: () -> Unit
) {
    // Definiramo tople, “coffee” nijanse
    val darkBackground = Color(0xFF2D2A2A)  // Tamno-siva/smeđa pozadina
    val darkCard = Color(0xFF3E2723)       // Tamna kava za kartice
    val coffeeDark = Color(0xFF3E2723)     // za gradient (gornji)
    val coffeeMedium = Color(0xFF6D4C41)   // svjetlija smeđa
    val gold = Color(0xFFFFD700)          // zlatna za zvjezdicu i naglaske
    val white = Color(0xFFFFFFFF)
    val lightGray = Color(0xFFD3D3D3)

    // Primjer ponuda koje ćemo prikazati u LazyRow
    val sampleOffers = listOf(
        OfferData("Zimska ponuda", R.drawable.offer),
        OfferData("Kava + Kolač", R.drawable.offer),
        OfferData("Happy Hour", R.drawable.offer)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
    ) {
        // 1) Gornji dio: coffee gradijent (zaglavlje)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            coffeeDark,    // tamnija smeđa
                            coffeeMedium   // svjetlija smeđa
                        )
                    )
                )
        )

        // 2) Sadržaj “preko” gradijenta
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // (a) Pozdrav + user info + bijeli tekst + ikonica
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lijevo: “Park's Cafe” (bijeli) + “Dobrodošli, userName”
                Column {
                    Text(
                        text = "Park's Cafe",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = white
                        )
                    )
                    Text(
                        text = "Dobrodošli, $userName",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = lightGray
                        )
                    )
                }
                // Desno: neka ikonica (kao primjer)
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home Icon",
                    tint = gold,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // (b) Kartica za prikaz bodova (fiksna zvjezdica bez animacije)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clickable { onCardClick() },
                colors = CardDefaults.cardColors(containerColor = darkCard),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lijevo: zvjezdica (fiksnih 40.dp)
                    Icon(
                        painter = painterResource(id = R.drawable.star),
                        contentDescription = "Star Icon",
                        tint = gold,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Desno: Tekst “Vaši bodovi” i broj
                    Column {
                        Text(
                            text = "Vaši bodovi",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = lightGray
                            )
                        )
                        Text(
                            text = "$points",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = white
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 3) “Aktivne ponude” (horizontalni prikaz s fade-in animacijom)
            Text(
                text = "Aktivne ponude",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = white
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sampleOffers.size) { index ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 600,
                                delayMillis = index * 150
                            )
                        ),
                        exit = fadeOut()
                    ) {
                        OfferCard(
                            offer = sampleOffers[index],
                            cardColor = darkCard,
                            textColor = white,
                            descColor = lightGray
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kartica za pojedinu ponudu.
 */
@Composable
fun OfferCard(
    offer: OfferData,
    cardColor: Color,
    textColor: Color,
    descColor: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .width(220.dp)
            .height(260.dp)
    ) {
        Column {
            // Gornja slika
            Image(
                painter = painterResource(id = offer.imageRes),
                contentDescription = offer.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                alignment = Alignment.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Naslov ponude
            Text(
                text = offer.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Opis
            Text(
                text = offer.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = descColor
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}



/**
 * 2) KARTICA (QR ekran)
 */
@Composable
fun CardScreen(
    points: Int,
    onScanClick: () -> Unit,
    onBack: () -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()

    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Dohvat QR koda iz Firestore-a (ako postoji)
    LaunchedEffect(user?.uid) {
        user?.let {
            db.collection("users")
                .document(it.uid)
                .get()
                .addOnSuccessListener { document ->
                    val qrCodeString = document.getString("qrCode") ?: ""
                    qrCodeBitmap = generateQRCode(qrCodeString)
                }
        }
    }

    // "Coffee" gradijent
    val coffeeDark = Color(0xFF3E2723)
    val coffeeMedium = Color(0xFF6D4C41)
    val gold = Color(0xFFFFD700)
    val white = Color(0xFFFFFFFF)

    // Držimo state za animaciju QR koda (fade-in)
    var showQR by remember { mutableStateOf(false) }

    // Pokreni lagani delay pa postavi showQR = true (da dobijemo "fade in" efekt)
    LaunchedEffect(Unit) {
        delay(300) // npr. 300ms
        showQR = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        coffeeDark,
                        coffeeMedium,
                        Color.Black // pri dnu prelazi u crnu
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Naslov
            Text(
                text = "Park's Card",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = white,
                modifier = Modifier.padding(top = 32.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Kratki opis
            Text(
                text = "Pokažite ovaj kod za skupljanje bodova.",
                fontSize = 14.sp,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Kartica s bodovima i QR-om
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f, fill = false),
                colors = CardDefaults.cardColors(containerColor = coffeeDark),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Naslov u kartici
                    Text(
                        text = "Vaši bodovi",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = white
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bodovi + zvjezdica
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = points.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = gold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.star),
                            contentDescription = "Star icon",
                            tint = gold,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Fade in za QR
                    AnimatedVisibility(
                        visible = showQR,
                        enter = fadeIn(animationSpec = tween(durationMillis = 600)),
                        exit = fadeOut()
                    ) {
                        if (qrCodeBitmap != null) {
                            Image(
                                bitmap = qrCodeBitmap!!.asImageBitmap(),
                                contentDescription = "QR Kod",
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            CircularProgressIndicator(color = gold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gumbi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // "Skeniraj / Sakupi bodove"
                Button(
                    onClick = onScanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = coffeeMedium),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Skeniraj / Sakupi bodove",
                        color = white,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // "Nazad" sa strelicom
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = coffeeMedium),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Nazad",
                        tint = white
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nazad",
                        color = white,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 3) LOGIN SCREEN
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    onBack: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    // Boje (coffee tema)
    val coffeeDark = Color(0xFF3E2723)
    val coffeeMedium = Color(0xFF6D4C41)
    val gold = Color(0xFFFFD700)
    val white = Color(0xFFFFFFFF)

    // Google Sign-In
    val context = LocalContext.current
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("466821096480-e70qg8jhvsqssnemjebi9kbs448lvqd7.apps.googleusercontent.com")
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    // Ovo je već implementirano
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(Exception::class.java)
                if (account != null) {
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { signInTask ->
                            if (signInTask.isSuccessful) {
                                // Ako je prvi put, kreiraj dokument
                                val user = auth.currentUser
                                user?.let {
                                    createUserDocumentIfNotExists(db, it.uid)
                                }
                                onLoginSuccess()
                            } else {
                                errorMessage = signInTask.exception?.message ?: "Google Sign-In neuspješan"
                            }
                        }
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Google Sign-In error"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(coffeeDark, coffeeMedium, Color.Black)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ikonica (opcionalno)
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Prijava",
                tint = gold,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Prijava",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = white,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Kartica
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = coffeeDark),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = gold,
                            unfocusedLabelColor = Color.LightGray,
                            focusedBorderColor = gold,
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = white,
                            unfocusedTextColor = white,
                            cursorColor = gold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Lozinka
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Lozinka") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = gold,
                            unfocusedLabelColor = Color.LightGray,
                            focusedBorderColor = gold,
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = white,
                            unfocusedTextColor = white,
                            cursorColor = gold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Gumb "Prijavi se"
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Email i lozinka su obavezni!"
                                return@Button
                            }
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { signInTask ->
                                    if (signInTask.isSuccessful) {
                                        // Stvori dokument ako ne postoji
                                        val user = auth.currentUser
                                        user?.let { createUserDocumentIfNotExists(db, it.uid) }
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = signInTask.exception?.message
                                            ?: "Neuspješna prijava."
                                    }
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = gold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Prijavi se", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // **Gumb za Google Sign-In** (jasno istaknut):
                    Button(
                        onClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            googleSignInLauncher.launch(signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = gold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google), // Ikonica (ako je imaš)
                            contentDescription = "Google Icon",
                            tint = Color.Unspecified, // Ako želiš da ostane originalna boja ikone
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nastavi s Googleom", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Gumb "Registriraj se"
                    Button(
                        onClick = onRegisterClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E7B6A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Registriraj se", color = white)
                    }

                    // Error poruka
                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nazad
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = coffeeMedium),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Nazad", color = white)
            }
        }
    }
}




/**
 * Pomoćna metoda - kreira user dokument ako ne postoji
 */
fun createUserDocumentIfNotExists(db: FirebaseFirestore, uid: String) {
    val docRef = db.collection("users").document(uid)
    docRef.get().addOnSuccessListener { snapshot ->
        if (!snapshot.exists()) {
            val newData = mapOf(
                "points" to 0,
                "qrCode" to UUID.randomUUID().toString()
            )
            docRef.set(newData)
        }
    }
}

/**
 * 4) REGISTRACIJA
 */
@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onBack: () -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Registracija", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("Ime") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Prezime") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Lozinka") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (email.isBlank() || password.isBlank() || firstName.isBlank() || lastName.isBlank()) {
                errorMessage = "Sva polja moraju biti popunjena!"
                return@Button
            }
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val userData = mapOf(
                            "firstName" to firstName,
                            "lastName" to lastName,
                            "email" to email,
                            "points" to 0,
                            "qrCode" to UUID.randomUUID().toString()
                        )
                        user?.let {
                            db.collection("users").document(it.uid)
                                .set(userData)
                                .addOnSuccessListener {
                                    onRegisterSuccess()
                                }
                                .addOnFailureListener { e ->
                                    errorMessage = "Greška: ${e.message}"
                                }
                        }
                    } else {
                        errorMessage = task.exception?.message ?: "Registracija neuspješna."
                    }
                }
        }) {
            Text("Registriraj se")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBack) {
            Text("Nazad")
        }
    }
}

/**
 * 5) **REDIZAJNIRANA** NAGRADE (RewardsScreen)
 */
data class RewardItem(
    val title: String,
    val description: String,
    val requiredPoints: Int,
    val imageRes: Int? = null // Ako želiš eventualnu sliku
)

/**
 * RewardsScreen:
 *  - "userPoints": trenutni bodovi korisnika
 *  - "onBack": lambda koja se poziva kad se klikne gumb "Nazad"
 *  - "onRedeem": što raditi kad korisnik iskoristi neku nagradu
 */
@Composable
fun RewardsScreen(
    userPoints: Int,
    onBack: () -> Unit,
    onRedeem: (RewardItem) -> Unit
) {
    // Primjer liste nagrada (može se dohvatiti iz Firestore-a ili sl.)
    val rewardList = listOf(
        RewardItem(
            title = "Besplatna kava",
            description = "Iskoristi 50 bodova za besplatnu kavu!",
            requiredPoints = 50,
            imageRes = R.drawable.offer
        ),
        RewardItem(
            title = "Popust 20% na desert",
            description = "Uštedi na omiljenom kolaču ili torti.",
            requiredPoints = 30,
            imageRes = R.drawable.offer
        ),
        RewardItem(
            title = "2+1 gratis",
            description = "Pri kupnji 2 pića, treće dobivaš besplatno.",
            requiredPoints = 75,
            imageRes = R.drawable.offer
        )
    )

    // “Coffee” gradijent boje (slično kao i na ostalim ekranima)
    val coffeeDark = Color(0xFF3E2723)     // tamno-smeđa
    val coffeeMedium = Color(0xFF6D4C41)   // svjetlija smeđa
    val gold = Color(0xFFFFD700)          // zlatna
    val white = Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        coffeeDark,
                        coffeeMedium,
                        Color.Black // prema dnu postaje još tamnije
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1) Naslov i prikaz bodova na vrhu
            Text(
                text = "Nagrade",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = white
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Vaši bodovi: ",
                    fontSize = 18.sp,
                    color = white
                )
                Text(
                    text = userPoints.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = gold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2) Lista nagrada (LazyColumn s animiranim itemima)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(rewardList) { index, reward ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = index * 150
                            )
                        ),
                        exit = fadeOut()
                    ) {
                        RewardCard(
                            reward = reward,
                            userPoints = userPoints,
                            onRedeem = onRedeem
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3) Gumb “Nazad”
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = coffeeMedium),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Nazad",
                    color = white,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Pojedinačna kartica nagrade:
 *  - Prikaže sliku (opcionalno),
 *  - Naziv, opis, potrebne bodove,
 *  - Gumb "Iskoristi" (enabled samo ako userPoints >= reward.requiredPoints).
 */
@Composable
fun RewardCard(
    reward: RewardItem,
    userPoints: Int,
    onRedeem: (RewardItem) -> Unit
) {
    val cardColor = Color(0xFF3E2723) // tamno-smeđa podloga za karticu
    val gold = Color(0xFFFFD700)
    val white = Color(0xFFFFFFFF)
    val lightGray = Color(0xFFD3D3D3)

    // Provjera ima li korisnik dovoljno bodova
    val canRedeem = userPoints >= reward.requiredPoints

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Slika (ako je postavljena)
            reward.imageRes?.let { imageRes ->
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = reward.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Naziv nagrade
            Text(
                text = reward.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = white
                )
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Opis
            Text(
                text = reward.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = lightGray
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Potrebni bodovi
            Text(
                text = "Potrebno bodova: ${reward.requiredPoints}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = gold
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Gumb "Iskoristi" (ako ima dovoljno bodova)
            Button(
                onClick = { onRedeem(reward) },
                enabled = canRedeem,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canRedeem) Color(0xFF6D4C41) else Color.Gray
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (canRedeem) "Iskoristi" else "Nedovoljno bodova",
                    color = white
                )
            }
        }
    }
}

/**
 * 6) PROFIL SCREEN
 */
@Composable
fun ProfileScreen(
    user: FirebaseUser,
    userName: String,
    onNavigateToActivity: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onLogout: () -> Unit
) {
    val darkBackground = Color(0xFF2D2A2A)
    val coffeeDark = Color(0xFF3E2723)
    val coffeeMedium = Color(0xFF6D4C41)
    val cardColor = Color(0xFF3E2723)
    val gold = Color(0xFFFFD700)
    val white = Color(0xFFFFFFFF)
    val lightGray = Color(0xFFD3D3D3)

    // Omogućimo scroll jer je profilna stranica obično dulja
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // 1) Zaglavlje (header) s gradijentom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(coffeeDark, coffeeMedium)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "User Icon",
                    tint = gold,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Moj profil",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = white
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userName,
                    fontSize = 16.sp,
                    color = lightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2) Kartica s opcijama (bez “Iskoristi kupon”)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // Nedavna aktivnost
                ProfileOptionItem(
                    icon = Icons.Default.History,
                    title = "Nedavna aktivnost",
                    onClick = onNavigateToActivity
                )
                Divider(color = Color.Gray, thickness = 1.dp)

                // Promjena lozinke
                ProfileOptionItem(
                    icon = Icons.Default.Lock,
                    title = "Promjena lozinke",
                    onClick = onNavigateToChangePassword
                )
                Divider(color = Color.Gray, thickness = 1.dp)

                // Kontakt i podrška
                ProfileOptionItem(
                    icon = Icons.Default.SupportAgent,
                    title = "Kontakt i podrška",
                    onClick = onNavigateToSupport
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3) Gumb “Odjavi se”
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Odjavi se",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Jedna opcija u profilu: ikona + naslov, unutar Row-a
 */
@Composable
fun ProfileOptionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFFFD700), // zlatna
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = Color.White
        )
    }
}

/**
 * Generiranje QR koda (koristi se npr. u CardScreen)
 */
fun generateQRCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * 7) KONTAKT I PODRŠKA
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val coffeeDark = Color(0xFF3E2723)
    val coffeeMedium = Color(0xFF6D4C41)
    val white = Color(0xFFFFFFFF)
    val gold = Color(0xFFFFD700)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(coffeeDark, coffeeMedium, Color.Black)
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Naslov
            Text(
                text = "Kontakt i podrška",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = white,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(top = 32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Kartica s detaljima
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f, fill = false),
                colors = CardDefaults.cardColors(containerColor = coffeeDark),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Email podrške: support@example.com",
                        style = MaterialTheme.typography.bodyLarge.copy(color = white)
                    )
                    Text(
                        text = "Telefon: +385 123 456 789",
                        style = MaterialTheme.typography.bodyLarge.copy(color = white)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Ako imate poteškoća ili pitanja oko aplikacije, slobodno nam se javite.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gumb "Nazad"
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = coffeeMedium),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Nazad", color = white, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


/**
 * 8) ISKORISTI KUPON
 */
@Composable
fun RedeemCouponScreen(onBack: () -> Unit) {
    var couponCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Iskoristi kupon", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Unesite kod kupona:")
        OutlinedTextField(
            value = couponCode,
            onValueChange = { couponCode = it },
            label = { Text("Kod kupona") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Logika iskorištavanja kupona (npr. dohvaćanje iz Firestore-a, validacija itd.)
        Button(onClick = {
            // Ovdje odradi validaciju i ažuriranje bodova ili što god treba
        }) {
            Text("Iskoristi kupon")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Nazad")
        }
    }
}

/**
 * 9) PROMJENA LOZINKE
 *    (Već postojeći kod; ostavljamo kako je ili ga dodatno prilagodimo)
 */
@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    // Boje (coffee tema)
    val coffeeDark = Color(0xFF3E2723)
    val coffeeMedium = Color(0xFF6D4C41)
    val white = Color(0xFFFFFFFF)
    val gold = Color(0xFFFFD700)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(coffeeDark, coffeeMedium, Color.Black)
                )
            )
            .padding(16.dp)
    ) {
        // Glavni stupac
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Naslov
            Text(
                text = "Promjena lozinke",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = white,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(top = 32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Kartica
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f, fill = false),
                colors = CardDefaults.cardColors(containerColor = coffeeDark),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Polje za trenutnu lozinku
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Trenutna lozinka") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = gold,
                            unfocusedLabelColor = Color.LightGray,
                            focusedBorderColor = gold,
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = white,
                            unfocusedTextColor = white,
                            cursorColor = gold
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Polje za novu lozinku
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nova lozinka") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = gold,
                            unfocusedLabelColor = Color.LightGray,
                            focusedBorderColor = gold,
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = white,
                            unfocusedTextColor = white,
                            cursorColor = gold
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gumb za promjenu lozinke
                    Button(
                        onClick = {
                            if (currentPassword.isBlank() || newPassword.isBlank()) {
                                message = "Oba polja moraju biti popunjena!"
                                return@Button
                            }
                            user?.let { firebaseUser ->
                                val email = firebaseUser.email
                                if (email == null) {
                                    message = "Ne mogu pronaći email korisnika."
                                    return@Button
                                }
                                // Re-authenticate
                                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                                firebaseUser.reauthenticate(credential)
                                    .addOnSuccessListener {
                                        // Kad uspije re-auth, promijeni lozinku
                                        firebaseUser.updatePassword(newPassword)
                                            .addOnSuccessListener {
                                                message = "Lozinka uspješno promijenjena."
                                            }
                                            .addOnFailureListener { e ->
                                                message = "Greška prilikom promjene lozinke: ${e.message}"
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        message = "Neuspješna autentikacija: ${e.message}"
                                    }
                            } ?: run {
                                message = "Nema korisnika u sessionu."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = gold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Promijeni lozinku", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Prikaz poruke
                    Text(message, color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gumb za "Nazad"
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = coffeeMedium),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Nazad", color = white)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


/**
 * 10) NEDAVNA AKTIVNOST
 *     Dohvatimo iz subkolekcije "recentActivities"
 */
@Composable
fun RecentActivityScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    var activityList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()

    // Dohvat aktivnosti iz Firestore-a
    LaunchedEffect(user?.uid) {
        user?.let {
            db.collection("users").document(it.uid)
                .collection("recentActivities")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && !snapshot.isEmpty) {
                        val activities = snapshot.documents.mapNotNull { doc -> doc.data }
                        // Najnovije prve
                        activityList = activities.reversed()
                    }
                }
        }
    }

    // Boje (coffee stil)
    val coffeeDark = Color(0xFF3E2723)
    val coffeeMedium = Color(0xFF6D4C41)
    val white = Color(0xFFFFFFFF)
    val gold = Color(0xFFFFD700)

    // Glavni container s gradijentom
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(coffeeDark, coffeeMedium, Color.Black)
                )
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Naslov
            Text(
                text = "Nedavna aktivnost",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = white,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Prikaz aktivnosti u LazyColumn
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activityList) { activityItem ->
                    val product = activityItem["product"] as? String ?: "Nepoznato"
                    val points = activityItem["points"] as? Long ?: 0
                    val operation = activityItem["operation"] as? String ?: "Operacija"
                    // Ako želiš formatirati timestamp, možeš izvući FieldValue
                    // ili doc["timestamp"] itd.

                    ActivityCard(
                        product = product,
                        points = points,
                        operation = operation
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gumb "Nazad"
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = coffeeMedium),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Nazad", color = white, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Jednostavna kartica za prikaz jedne aktivnosti.
 */
@Composable
fun ActivityCard(
    product: String,
    points: Long,
    operation: String
) {
    val cardColor = Color(0xFF3E2723) // tamna smeđa
    val gold = Color(0xFFFFD700)
    val white = Color(0xFFFFFFFF)
    val lightGray = Color(0xFFD3D3D3)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Operacija
            Text(
                text = operation,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = gold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Proizvod
            Text(
                text = "Proizvod: $product",
                style = MaterialTheme.typography.bodyMedium.copy(color = white)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Bodovi
            Text(
                text = "Bodovi: $points",
                style = MaterialTheme.typography.bodySmall.copy(color = lightGray)
            )
        }
    }
}

