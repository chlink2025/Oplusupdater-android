package com.houvven.oplusupdater.domain


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateQueryResponse(
    @SerialName("aid")
    val aid: String? = null,
    @SerialName("androidVersion")
    val androidVersion: String? = null,
    @SerialName("betaTasteInteract")
    val betaTasteInteract: Boolean? = null,
    @SerialName("colorOSVersion")
    val colorOSVersion: String? = null,
    @SerialName("componentAssembleType")
    val componentAssembleType: Boolean? = null,
    @SerialName("components")
    val components: List<Component?>? = null,
    @SerialName("decentralize")
    val decentralize: Decentralize? = null,
    @SerialName("description")
    val description: Description? = null,
    @SerialName("features")
    val features: Features? = null,
    @SerialName("gkaReq")
    val gkaReq: Int? = null,
    @SerialName("googlePatchInfo")
    val googlePatchInfo: String? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("isConfidential")
    val isConfidential: Int? = null,
    @SerialName("isNvDescription")
    val isNvDescription: Boolean? = null,
    @SerialName("isRecruit")
    val isRecruit: Boolean? = null,
    @SerialName("isSecret")
    val isSecret: Boolean? = null,
    @SerialName("isV5GkaVersion")
    val isV5GkaVersion: Int? = null,
    @SerialName("nightUpdateLimit")
    val nightUpdateLimit: String? = null,
    @SerialName("noticeType")
    val noticeType: Int? = null,
    @SerialName("nvId16")
    val nvId16: String? = null,
    @SerialName("opexInfo")
    val opexInfo: List<OpexInfo?>? = null,
    @SerialName("osVersion")
    val osVersion: String? = null,
    @SerialName("otaVersion")
    val otaVersion: String? = null,
    @SerialName("paramFlag")
    val paramFlag: Int? = null,
    @SerialName("parent")
    val parent: String? = null,
    @SerialName("publishedTime")
    val publishedTime: Long? = null,
    @SerialName("realAndroidVersion")
    val realAndroidVersion: String? = null,
    @SerialName("realOsVersion")
    val realOsVersion: String? = null,
    @SerialName("realOtaVersion")
    val realOtaVersion: String? = null,
    @SerialName("realVersionName")
    val realVersionName: String? = null,
    @SerialName("reminderType")
    val reminderType: Int? = null,
    @SerialName("reminderValue")
    val reminderValue: ReminderValue? = null,
    @SerialName("rid")
    val rid: String? = null,
    @SerialName("securityPatch")
    val securityPatch: String? = null,
    @SerialName("securityPatchVendor")
    val securityPatchVendor: String? = null,
    @SerialName("silenceUpdate")
    val silenceUpdate: Int? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("versionCode")
    val versionCode: Int? = null,
    @SerialName("versionName")
    val versionName: String? = null,
    @SerialName("versionTypeH5")
    val versionTypeH5: String? = null,
    @SerialName("versionTypeId")
    val versionTypeId: String? = null
) {
    @Serializable
    data class Component(
        @SerialName("componentId")
        val componentId: String? = null,
        @SerialName("componentName")
        val componentName: String? = null,
        @SerialName("componentPackets")
        val componentPackets: ComponentPackets? = null,
        @SerialName("componentVersion")
        val componentVersion: String? = null
    ) {
        @Serializable
        data class ComponentPackets(
            @SerialName("id")
            val id: String? = null,
            @SerialName("manualUrl")
            val manualUrl: String? = null,
            @SerialName("md5")
            val md5: String? = null,
            @SerialName("size")
            val size: String? = null,
            @SerialName("type")
            val type: String? = null,
            @SerialName("url")
            val url: String? = null,
            @SerialName("vabInfo")
            val vabInfo: VabInfo? = null
        ) {
            @Serializable
            data class VabInfo(
                @SerialName("data")
                val `data`: Data? = null
            ) {
                @Serializable
                data class Data(
                    @SerialName("extra_params")
                    val extraParams: String? = null,
                    @SerialName("header")
                    val header: List<String?>? = null,
                    @SerialName("otaStreamingProperty")
                    val otaStreamingProperty: String? = null,
                    @SerialName("vab_package_hash")
                    val vabPackageHash: String? = null
                )
            }
        }
    }

    @Serializable
    data class Decentralize(
        @SerialName("offset")
        val offset: Int? = null,
        @SerialName("round")
        val round: Int? = null,
        @SerialName("strategyVersion")
        val strategyVersion: String? = null
    )

    @Serializable
    data class Description(
        @SerialName("opex")
        val opex: Opex? = null,
        @SerialName("panelUrl")
        val panelUrl: String? = null,
        @SerialName("share")
        val share: String? = null,
        @SerialName("url")
        val url: String? = null
    ) {
        @Serializable
        data class Opex(
            @SerialName("content")
            val content: String? = null,
            @SerialName("title")
            val title: String? = null
        )
    }

    @Serializable
    data class Features(
        @SerialName("featureName")
        val featureName: String? = null,
        @SerialName("message")
        val message: List<Message?>? = null,
        @SerialName("url")
        val url: String? = null
    ) {
        @Serializable
        data class Message(
            @SerialName("backgroundColor")
            val backgroundColor: String? = null,
            @SerialName("content")
            val content: String? = null,
            @SerialName("order")
            val order: Int? = null,
            @SerialName("title")
            val title: String? = null,
            @SerialName("url")
            val url: String? = null
        )
    }

    @Serializable
    data class OpexInfo(
        @SerialName("aid")
        val aid: String? = null,
        @SerialName("versionCode")
        val versionCode: String? = null
    )

    @Serializable
    data class ReminderValue(
        @SerialName("download")
        val download: Download? = null,
        @SerialName("upgrade")
        val upgrade: Upgrade? = null
    ) {
        @Serializable
        data class Download(
            @SerialName("notice")
            val notice: List<Int?>? = null,
            @SerialName("pop")
            val pop: List<Int?>? = null,
            @SerialName("version")
            val version: String? = null
        )

        @Serializable
        data class Upgrade(
            @SerialName("notice")
            val notice: List<Int?>? = null,
            @SerialName("pop")
            val pop: List<Int?>? = null,
            @SerialName("version")
            val version: String? = null
        )
    }
}