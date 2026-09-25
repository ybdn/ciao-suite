package dev.ybdn.ciao.clavier.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciao.clavier.R
import dev.ybdn.ciao.designsystem.components.NeoButton
import dev.ybdn.ciao.designsystem.components.NeoCard
import dev.ybdn.ciao.designsystem.components.NeoScreen
import dev.ybdn.ciao.designsystem.components.NeoSectionHeader
import dev.ybdn.ciao.designsystem.components.NeoTone

private const val SourceCodeUrl = "https://github.com/ybdn/ciao-suite"
private const val PrivacyPolicyUrl = "https://github.com/ybdn/ciao-suite/blob/main/apps/clavier/PRIVACY.md"

/**
 * À propos (apps/clavier/docs/spec-v1.md §10.4) : version, licences (dictionnaire, données
 * Unicode, polices), lien vers le code source et la politique de confidentialité.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    // minSdk 33 : la variante avec drapeaux, sans celle dépréciée qui prend un Int.
    val versionName = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        .versionName ?: "?"

    fun openUrl(url: String) {
        // Aucun navigateur installé : rien à ouvrir, mais pas de plantage.
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    NeoScreen(
        title = stringResource(R.string.about_title),
        navigation = {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.personal_back))
            }
        },
    ) {
        NeoCard {
            NeoSectionHeader("01", stringResource(R.string.about_version))
            Text(versionName, style = MaterialTheme.typography.bodyMedium)
        }

        NeoCard {
            NeoSectionHeader("02", stringResource(R.string.about_licenses_title))
            Text(stringResource(R.string.about_license_dictionary), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_license_unicode), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_license_fonts), style = MaterialTheme.typography.bodySmall)
        }

        NeoCard {
            NeoSectionHeader("03", stringResource(R.string.about_links_title))
            NeoButton(
                text = stringResource(R.string.about_source_code),
                tone = NeoTone.Surface,
                onClick = { openUrl(SourceCodeUrl) },
            )
            NeoButton(
                text = stringResource(R.string.about_privacy_policy),
                tone = NeoTone.Surface,
                onClick = { openUrl(PrivacyPolicyUrl) },
            )
        }
    }
}
