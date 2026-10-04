# ZEROCHILL app-owned stock UI audit

Base: `4f01b4437de2cf1faf6c9f68764db86992609091` on `rebrand/zerochill`. This audit covers production `app/src/main/java` and `app/src/main/res`, not the separate feedback admin application or tests. Line lists classify every remaining symbol occurrence after the consistency pass.

Categories: 1 branded ZEROCHILL UI, 2 internal widget or type with custom appearance, 3 Android-owned system surface kept native, 4 intentional exception. Android keyboard, Autofill, document picker, share sheet, permissions, and installer use OS UI; these are category 3 at their system invocation sites and do not appear in the symbol list below.

| Symbol | Category | File and lines |
|---|---|---|
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `AppBackupController.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `ChaosFeedView.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `DownloadedActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `FavoritesActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `FeedViewStyleController.java`: 3, 94, 148 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `GestureGuideDialog.java`: 4, 174, 181, 182 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `InlineCommentsDialog.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `MainActivity.java`: 5 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `NativeFeedBrowserActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `NativeMainActivity.java`: 4, 1009, 1015, 1121 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `NotificationCoordinator.java`: 5, 181, 187, 188 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `PlayerActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `SettingsActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `UnifiedVideoController.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `UpdateInboxActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `VideoDetailActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `ZeroChillAccountSecurityActivity.java`: 4, 137, 144, 148, 154 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `ZeroChillDialog.java`: 3, 12, 16, 36, 37 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `ZeroChillMessageActivity.java`: 4 |
| `AlertDialog` | 1. Shared branded dialog shell or its retained callback/button contract. | `ZeroChillPublicProfileActivity.java`: 4 |
| `AlertDialog` | 4. Existing branded first-run notice; its noncancelable acceptance shell and persistence stay intact. | `AccessNoticeDialog.java`: 4, 96, 164, 165, 166, 167, 169, 170 |
| `CheckBox` | 1. Shared branded checkbox, or semantic type backed by it. | `StartupWizardActivity.java`: 283 |
| `CheckBox` | 1. Shared branded checkbox, or semantic type backed by it. | `ZeroChillAccountActivity.java`: 376, 377, 386, 387, 925, 926 |
| `CheckBox` | 1. Shared branded checkbox, or semantic type backed by it. | `ZeroChillCheckBox.java`: 5, 8, 9 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `CreatorsActivity.java`: 59 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `DownloadedActivity.java`: 112 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `FavoritesActivity.java`: 137 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `FeedbackActivity.java`: 121 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `FeedbackThreadActivity.java`: 97 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `InlineCommentsDialog.java`: 216 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `NativeMainActivity.java`: 1112 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `SearchActivity.java`: 270 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `ZeroChillAccountActivity.java`: 906 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `ZeroChillAccountSecurityActivity.java`: 129, 190 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `ZeroChillEditText.java`: 5, 8, 9 |
| `EditText` | 1. Shared branded input with native keyboard/Autofill. | `ZeroChillMessageActivity.java`: 197 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `CreatorSuggestionsController.java`: 7, 24, 45, 52, 58 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `CreatorsActivity.java`: 14, 37 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `DownloadedActivity.java`: 17, 47 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `FavoritesActivity.java`: 16, 73 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `FeedbackActivity.java`: 11, 31 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `FeedbackThreadActivity.java`: 10, 38 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `InlineCommentsDialog.java`: 17, 47 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `NativeMainActivity.java`: 26 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `SearchActivity.java`: 18, 62 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `SocialActivityCoordinator.java`: 151 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `ZeroChillAccountActivity.java`: 20, 53, 54, 353, 360, 367, 385, 667, 679, 905 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `ZeroChillAccountSecurityActivity.java`: 12, 67, 92, 93, 189 |
| `EditText` | 2. Input type, parameter, or instanceof check; actual field uses branded input. | `ZeroChillMessageActivity.java`: 17, 83 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `BunkrGalleryActivity.java`: 324 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `ChaosFeedView.java`: 193, 1673 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `InlineCommentsDialog.java`: 186 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `MainActivity.java`: 206 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `MemeViewerActivity.java`: 103 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `UnifiedVideoController.java`: 280 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `UpdateCardController.java`: 95 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `VideoDetailActivity.java`: 413, 517 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `WebFallbackActivity.java`: 125 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `ZeroChillAccountActivity.java`: 265 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `ZeroChillInboxActivity.java`: 166 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `ZeroChillMessageActivity.java`: 188 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `ZeroChillProgressBar.java`: 11, 14, 15, 21 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `ZeroChillPublicProfileActivity.java`: 112 |
| `ProgressBar` | 1. Shared branded dots or cyan horizontal bar. | `ZeroChillSocialSettingsActivity.java`: 88 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `BunkrGalleryActivity.java`: 21, 100 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `ChaosFeedView.java`: 27, 144, 1596 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `DownloadedActivity.java`: 21 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `FavoritesActivity.java`: 20 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `InlineCommentsDialog.java`: 21, 50 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `MainActivity.java`: 44, 114 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `MainPagerAdapter.java`: 10 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `MemeViewerActivity.java`: 15 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `NativeFeedBrowserActivity.java`: 23 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `NativeMainActivity.java`: 30 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `UiPolishController.java`: 7, 127, 128 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `UnifiedVideoController.java`: 28, 184 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `UpdateCardController.java`: 16, 38 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `VideoDetailActivity.java`: 35, 127, 129 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `WebFallbackActivity.java`: 24, 37 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `ZeroChillAccountActivity.java`: 24, 44 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `ZeroChillInboxActivity.java`: 17, 47 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `ZeroChillMessageActivity.java`: 20, 82 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `ZeroChillPublicProfileActivity.java`: 18, 34 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `ZeroChillSocialSettingsActivity.java`: 13, 29 |
| `ProgressBar` | 2. ProgressBar type/style helper; instances use branded dots or cyan horizontal bars. | `ZeroChillUi.java`: 15, 130 |
| `Switch` | 1. Shared branded switch, or semantic type backed by it. | `ZeroChillSocialSettingsActivity.java`: 15, 36, 252, 281 |
| `Switch` | 1. Shared branded switch, or semantic type backed by it. | `ZeroChillSwitch.java`: 4, 7 |
| `Toast` | 1. Shared in-app transient component or its call site. | `AppBackupController.java`: 76 |
| `Toast` | 1. Shared in-app transient component or its call site. | `AppUpdater.java`: 59, 73, 96, 99, 102, 110, 113, 427, 430, 456 |
| `Toast` | 1. Shared in-app transient component or its call site. | `BunkrGalleryActivity.java`: 419, 453, 458, 466, 473, 544, 547, 558, 899, 958, 1027, 1032, 1227 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ChaosFeedView.java`: 1240, 1406, 1410, 1425, 1428, 1995, 1998, 2289, 2292 |
| `Toast` | 1. Shared in-app transient component or its call site. | `CrazyShitApplication.java`: 69, 79 |
| `Toast` | 1. Shared in-app transient component or its call site. | `CreatorAvatarCropActivity.java`: 179, 265, 292 |
| `Toast` | 1. Shared in-app transient component or its call site. | `CreatorsActivity.java`: 563 |
| `Toast` | 1. Shared in-app transient component or its call site. | `FavoritesActivity.java`: 431 |
| `Toast` | 1. Shared in-app transient component or its call site. | `FeedViewStyleController.java`: 88 |
| `Toast` | 1. Shared in-app transient component or its call site. | `FeedbackActivity.java`: 213, 219 |
| `Toast` | 1. Shared in-app transient component or its call site. | `FeedbackThreadActivity.java`: 128, 131, 227 |
| `Toast` | 1. Shared in-app transient component or its call site. | `GalleryMediaDownloader.java`: 250 |
| `Toast` | 1. Shared in-app transient component or its call site. | `InlineCommentsDialog.java`: 278, 531, 607, 669 |
| `Toast` | 1. Shared in-app transient component or its call site. | `MainActivity.java`: 468, 524, 551, 553, 699, 701, 723, 725, 785, 787, 843, 845, 1082, 1084, 1101, 1159, 1161, 1163, 1165, 1310, 1312, 1370, 1372, 1378, 1381, 1500, 1502 |
| `Toast` | 1. Shared in-app transient component or its call site. | `MainPagerAdapter.java`: 604, 607, 691, 694, 1000, 1003 |
| `Toast` | 1. Shared in-app transient component or its call site. | `NativeCategoryAdapter.java`: 399, 404 |
| `Toast` | 1. Shared in-app transient component or its call site. | `NativeFeedBrowserActivity.java`: 1340, 1343, 1352, 1366, 1570, 1573, 1623, 1629, 1845, 1848 |
| `Toast` | 1. Shared in-app transient component or its call site. | `NativeMainActivity.java`: 851, 896, 899, 1004, 1096, 1099 |
| `Toast` | 1. Shared in-app transient component or its call site. | `NativeMiniPlayer.java`: 204 |
| `Toast` | 1. Shared in-app transient component or its call site. | `OnlyFapHubView.java`: 1560, 1565 |
| `Toast` | 1. Shared in-app transient component or its call site. | `PlayerActivity.java`: 541, 546, 549 |
| `Toast` | 1. Shared in-app transient component or its call site. | `SearchActivity.java`: 742, 745 |
| `Toast` | 1. Shared in-app transient component or its call site. | `SettingsActivity.java`: 52, 55, 510, 513, 540, 543, 553, 556, 575, 660, 665 |
| `Toast` | 1. Shared in-app transient component or its call site. | `UiFoundationCoordinator.java`: 34 |
| `Toast` | 1. Shared in-app transient component or its call site. | `UnifiedVideoController.java`: 939, 942 |
| `Toast` | 1. Shared in-app transient component or its call site. | `UpdateInboxActivity.java`: 741, 744 |
| `Toast` | 1. Shared in-app transient component or its call site. | `VideoDetailActivity.java`: 1296, 1299, 1772, 1775, 1791, 1794 |
| `Toast` | 1. Shared in-app transient component or its call site. | `VideoDownloadStore.java`: 641 |
| `Toast` | 1. Shared in-app transient component or its call site. | `WatchStatePolish.java`: 58, 69, 72 |
| `Toast` | 1. Shared in-app transient component or its call site. | `WebFallbackActivity.java`: 224, 225 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillAccountActivity.java`: 198, 201, 207, 285, 403, 417, 435, 722, 744, 891, 896, 901 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillAccountSecurityActivity.java`: 111, 226, 232 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillDialog.java`: 38 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillInboxActivity.java`: 219, 222, 477 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillMessageActivity.java`: 238, 241, 286, 289, 333, 354, 427, 432, 435, 449, 452 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillPublicProfileActivity.java`: 607, 616, 619, 637, 640 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillSocialSettingsActivity.java`: 186, 241 |
| `Toast` | 1. Shared in-app transient component or its call site. | `ZeroChillToast.java`: 20, 35, 41, 42, 45, 119 |

No production XML layout contains an unstyled `<ProgressBar>`, `<Switch>`, `<EditText>`, or `<CheckBox>`. The circular loading sites use `ZeroChillProgressBar` three dots; three determinate horizontal progress bars retain real progress values with cyan fill. The only `new AlertDialog.Builder` remains the already branded `AccessNoticeDialog`; it preserves its noncancelable first-run decision and acceptance key.

Native/system behavior retained: keyboard and Autofill from editable fields; file/document picker for backup and avatar; share sheet; permission prompts; package installer. No application ID, preference key, Supabase API, persisted data, or playback changes are part of this pass.

Validation: targeted Robolectric control behavior tests added. Local Android SDK/Gradle are unavailable in this workspace; CI build, unit tests, lint audit, and physical-device visual review remain pending.
