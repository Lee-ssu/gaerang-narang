package kr.gaerang

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

internal enum class PlayStyle(val label: String) {
    PLAY("같이 뛰기"), GREET("천천히 인사"), QUIET("우리끼리 산책")
}

internal enum class Boarding(val label: String) {
    CAR_SEAT("카시트"), SEAT("좌석에 직접"), HELD("안고 타요")
}

internal enum class Coat(val label: String, val face: Int, val body: Int, val resting: Int) {
    BROWN("갈색", R.drawable.dog_face_brown, R.drawable.dog_body_brown, R.drawable.dog_rest_brown),
    CREAM("크림색", R.drawable.dog_face_cream, R.drawable.dog_body_cream, R.drawable.dog_rest_cream),
    GRAY("회색", R.drawable.dog_face_gray, R.drawable.dog_body_gray, R.drawable.dog_rest_gray)
}

internal data class Dog(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val breed: String,
    val age: Int,
    val weight: Double,
    val seat: Seat = Seat.REAR_RIGHT,
    val style: PlayStyle = PlayStyle.GREET,
    val nursery: String = "",
    val boarding: Boarding = Boarding.CAR_SEAT,
    val motionSickness: Boolean = false,
    val coat: Coat = Coat.BROWN,
    val photo: String? = null
) {
    val description get() = "$breed · ${age}살 · ${formatWeight(weight)}kg"

    fun toJson() = JSONObject().put("id", id).put("name", name).put("breed", breed)
        .put("age", age).put("weight", weight).put("seat", seat.name).put("style", style.name)
        .put("nursery", nursery).put("boarding", boarding.name).put("motionSickness", motionSickness)
        .put("coat", coat.name).put("photo", photo)

    companion object {
        fun fromJson(json: JSONObject) = Dog(
            id = json.getString("id"), name = json.getString("name"), breed = json.getString("breed"),
            age = json.getInt("age"), weight = json.getDouble("weight"),
            seat = Seat.entries.find { it.name == json.optString("seat") } ?: Seat.REAR_RIGHT,
            style = PlayStyle.entries.find { it.name == json.optString("style") } ?: PlayStyle.GREET,
            nursery = json.optString("nursery"),
            boarding = Boarding.entries.find { it.name == json.optString("boarding") }
                ?: if (json.optBoolean("carSeat", true)) Boarding.CAR_SEAT else Boarding.SEAT,
            motionSickness = json.optBoolean("motionSickness"),
            coat = Coat.entries.find { it.name == json.optString("coat") } ?: Coat.BROWN,
            photo = json.optString("photo").takeIf(String::isNotEmpty)
        )
        val sample = Dog("demo", "초코", "토이 푸들", 4, 3.8)
    }
}

internal fun formatWeight(value: Double) =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

internal enum class PlaceKind(val label: String) {
    FIELD("넓은 곳"), CAFE("카페"), SEA("바닷가"), REST("쉼터"), CHARGE("충전·산책")
}
internal enum class AreaAccess { ALLOWED, DENIED, UNKNOWN }
internal enum class Eligibility { MATCH, OUTDOOR_ONLY, CHECK, EXCLUDED }

internal data class ChargeWalk(val walk: String, val connectors: String)

internal data class Place(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val area: String,
    val description: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val indoor: AreaAccess = AreaAccess.DENIED,
    val outdoor: AreaAccess = AreaAccess.ALLOWED,
    val indoorLimit: Double? = null,
    val outdoorLimit: Double? = null,
    val restrictedBreeds: Set<String> = emptySet(),
    val carrier: Boolean = false,
    val quiet: Boolean = false,
    val play: Boolean = false,
    val source: String? = null,
    val sourceDate: String? = null,
    val sample: Boolean = false,
    val chargeWalk: ChargeWalk? = null
) {
    val routable get() = !sample && latitude != null && longitude != null
    fun eligibility(dogs: List<Dog>, requireIndoor: Boolean): Eligibility {
        if (dogs.any { dog ->
                restrictedBreeds.any { it.replace(" ", "").equals(dog.breed.replace(" ", ""), ignoreCase = true) }
            }) return Eligibility.EXCLUDED
        val outside = outdoor == AreaAccess.ALLOWED && dogs.all { outdoorLimit == null || it.weight <= outdoorLimit }
        val inside = indoor == AreaAccess.ALLOWED && dogs.all { indoorLimit == null || it.weight <= indoorLimit }
        return if (requireIndoor) {
            when {
                inside -> Eligibility.MATCH
                indoor == AreaAccess.UNKNOWN -> Eligibility.CHECK
                outside -> Eligibility.OUTDOOR_ONLY
                else -> Eligibility.EXCLUDED
            }
        } else {
            when {
                outside || inside -> Eligibility.MATCH
                indoor == AreaAccess.UNKNOWN || outdoor == AreaAccess.UNKNOWN -> Eligibility.CHECK
                else -> Eligibility.EXCLUDED
            }
        }
    }

    fun conditions(): String = buildList {
        if (indoor == AreaAccess.ALLOWED) add(indoorLimit?.let { "실내 ${formatWeight(it)}kg 이하" } ?: "실내 동반")
        if (outdoor == AreaAccess.ALLOWED) add(outdoorLimit?.let { "야외 ${formatWeight(it)}kg 이하" } ?: "야외 동반")
        if (indoor == AreaAccess.UNKNOWN && kind == PlaceKind.CAFE) add("실내 조건 문의")
        if (carrier) add("이동장 필요")
        if (restrictedBreeds.isNotEmpty()) add("견종 제한")
    }.joinToString(" · ")
}

internal object Places {
    const val CHARGER_LOOKUP = "https://ev.or.kr/nportal/monitor/evMap.do"

    val all = listOf(
        Place("ganjeolgot", "간절곶", PlaceKind.SEA, "울산 울주군",
            "바다를 보며 잔디길을 걷기 좋아요. 목줄과 배변봉투를 챙겨요.",
            "울산광역시 울주군 서생면 간절곶해안길 205", 35.3625312, 129.3591079, quiet = true,
            source = "https://korean.visitkorea.or.kr/detail/rem_detail.do?cotid=691144a3-34d3-467f-a691-846639ff31f7"),
        Place("anian", "애니언파크", PlaceKind.FIELD, "울산 북구",
            "소형견·대형견 운동장이 있어요. 이용 시간과 입장 조건을 확인해요.",
            "울산광역시 북구 호계매곡6로 108", 35.63442, 129.36683, play = true,
            source = "https://webzine.ulsan.go.kr/contents/view.do?bbsId=BBSMSTR_000000000180&nttId=15761"),
        Place("taehwa", "태화강 국가정원", PlaceKind.FIELD, "울산 중구",
            "강과 정원 사이를 걷는 목줄 산책 코스예요.",
            "울산광역시 중구 샛강남길 5", 35.54708, 129.30157, quiet = true,
            source = "https://tour.ulsan.go.kr/tour/korean/unit/attrctn/view.ulsan?mId=001001000000000000&searchDvsn1=A&searchGubun=attrctn&unqId=28"),
        Place("badang", "간절바당", PlaceKind.CAFE, "울산 울주군",
            "반려견 동반 장소로 안내돼 있어요. 실내·체급 기준은 매장에 확인해요.",
            "울산광역시 울주군 서생면 신암해안길 11", indoor = AreaAccess.UNKNOWN, outdoor = AreaAccess.UNKNOWN),
        Place("crave", "크레이브커피바", PlaceKind.CAFE, "울산 남구",
            "매장 안내에 소·중·대형견 동반이 표시돼 있어요. 실내에선 리드줄을 착용해요.",
            "울산광역시 남구 신정로40번길 2", 35.5354411, 129.3224482,
            indoor = AreaAccess.ALLOWED,
            source = "https://ban-life.com/store/view?id=15695&type=s", sourceDate = "2026.11.15"),
        Place("gapyeong", "가평휴게소", PlaceKind.REST, "춘천 방향",
            "서울에서 강릉으로 갈 때 들를 수 있는 예시 쉼터예요.",
            "경기도 가평군 설악면 미사리로540번길 51", 37.70194030, 127.54680510,
            outdoor = AreaAccess.UNKNOWN),
        Place("pine", "소나무 정원 카페", PlaceKind.CAFE, "시연 장소",
            "실내에서 함께 쉬는 조건을 확인하는 예시예요.",
            indoor = AreaAccess.ALLOWED, indoorLimit = 15.0, quiet = true, sample = true),
        Place("small", "작은뜰 카페", PlaceKind.CAFE, "시연 장소",
            "실내 10kg 이하, 테라스는 체중 제한이 없는 예시예요.",
            indoor = AreaAccess.ALLOWED, indoorLimit = 10.0, sample = true),
        Place("terrace", "노을빛 테라스", PlaceKind.CAFE, "시연 장소",
            "이동장을 사용하는 실내 동반 조건 예시예요.",
            indoor = AreaAccess.ALLOWED, indoorLimit = 8.0, carrier = true, sample = true),
        // Charger listings correspond to the Korean Environment Corporation snapshot from August 7, 2025.
        Place("charge-anian", "애니언파크 옥외주차장 충전소", PlaceKind.CHARGE, "울산 북구",
            "충전소와 야외 반려견 운동장이 같은 부지에 있어요. 운동장 이용 시간과 입장 조건을 확인해요.",
            "울산광역시 북구 호계매곡6로 108", 35.63441848, 129.36682925, play = true,
            source = "https://webzine.ulsan.go.kr/contents/view.do?bbsId=BBSMSTR_000000000180&nttId=15761",
            chargeWalk = ChargeWalk("야외 반려견 운동장", "DC콤보 · AC완속")),
        Place("charge-taehwa", "태화강 국가정원 노상주차장 충전소", PlaceKind.CHARGE, "울산 중구",
            "중구청 부지의 노상주차장에서 태화강 국가정원 태화동 쪽으로 산책할 수 있어요. 반려견은 목줄을 착용하고 현장 출입 구역을 확인해요.",
            "울산광역시 중구 태화강국가정원길 75", 35.5528797, 129.2837372, quiet = true,
            source = "https://www.ulsan.go.kr/tour/korean/unit/attrctn/view.ulsan?mId=001001000000000000&searchGubun=attrctn&unqId=28",
            chargeWalk = ChargeWalk("태화강 국가정원 목줄 산책", "DC콤보 · AC완속")),
        Place("charge-ganjeolgot", "간절곶공원 충전소", PlaceKind.CHARGE, "울산 울주군",
            "간절곶공원 주차장 주변에서 바다를 보며 산책할 수 있어요. 간절곶 야외 구역은 목줄 착용 시 반려견과 입장할 수 있어요.",
            "울산광역시 울주군 서생면 대송리 산 30-2", 35.363694, 129.356056, quiet = true,
            source = "https://www.ulsan.go.kr/tour/korean/unit/attrctn/view.ulsan?mId=001001000000000000&unqId=14",
            chargeWalk = ChargeWalk("간절곶 해안 목줄 산책", "DC콤보"))
    )

    fun get(id: String?) = all.firstOrNull { it.id == id }
}

internal data class Visit(
    val id: String,
    val placeId: String,
    val dogNames: String,
    val time: Long,
    val note: String,
    val mood: String,
    val photo: String?,
    val coat: Coat
) {
    fun toJson() = JSONObject().put("id", id).put("place", placeId).put("dogs", dogNames)
        .put("time", time).put("note", note).put("mood", mood).put("photo", photo).put("coat", coat.name)
    companion object {
        fun fromJson(json: JSONObject) = Visit(
            json.getString("id"), json.getString("place"), json.getString("dogs"), json.getLong("time"),
            json.optString("note"), json.optString("mood"), json.optString("photo").takeIf(String::isNotEmpty),
            Coat.entries.find { it.name == json.optString("coat") } ?: Coat.BROWN
        )
    }
}

internal enum class Appearance { SYSTEM, LIGHT, DARK }

internal class OutingStore(context: Context) {
    private val preferences = context.getSharedPreferences("outings", Context.MODE_PRIVATE)
    var dogs = read("dogs", Dog::fromJson)
        private set
    var visits = read("visits", Visit::fromJson)
        private set
    var activeIds: Set<String>
        get() = preferences.getStringSet("activeDogs", emptySet()).orEmpty()
        set(value) { preferences.edit().putStringSet("activeDogs", value).apply() }
    var mood: PlayStyle?
        get() = PlayStyle.entries.find { it.name == preferences.getString("mood", null) }
        set(value) { preferences.edit().putString("mood", value?.name).apply() }
    var courseIds: List<String>
        get() = preferences.getString("course", "").orEmpty().split(',').filter(String::isNotBlank)
        set(value) { preferences.edit().putString("course", value.joinToString(",")).apply() }
    var careAudio: String?
        get() = preferences.getString("careAudio", null)
        set(value) { preferences.edit().putString("careAudio", value).apply() }
    var shareDestination: Boolean
        get() = preferences.getBoolean("shareDestination", false)
        set(value) { preferences.edit().putBoolean("shareDestination", value).apply() }
    var appearance: Appearance
        get() = Appearance.entries.firstOrNull { it.name == preferences.getString("appearance", null) }
            ?: Appearance.SYSTEM
        set(value) { preferences.edit().putString("appearance", value.name).apply() }
    var windowPresets: List<Int>
        get() = preferences.getString("windowPresets", null)?.split(',')
            ?.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 0..100 } }
            ?.takeIf { it.size == 3 } ?: listOf(10, 30, 50)
        set(value) { preferences.edit().putString("windowPresets", value.joinToString(",")).apply() }

    fun save(dog: Dog) {
        dogs = if (dogs.any { it.id == dog.id }) dogs.map { if (it.id == dog.id) dog else it } else dogs + dog
        preferences.edit().putString("dogs", JSONArray(dogs.map(Dog::toJson)).toString()).apply()
    }

    fun save(visit: Visit) {
        visits = listOf(visit) + visits.filterNot { it.id == visit.id }
        preferences.edit().putString("visits", JSONArray(visits.map(Visit::toJson)).toString()).apply()
    }

    fun delete(dog: Dog) {
        dogs = dogs.filterNot { it.id == dog.id }
        preferences.edit().putString("dogs", JSONArray(dogs.map(Dog::toJson)).toString()).apply()
        activeIds = activeIds - dog.id
    }

    private fun <T> read(key: String, convert: (JSONObject) -> T): List<T> {
        val data = runCatching { JSONArray(preferences.getString(key, "[]")) }.getOrElse { JSONArray() }
        return (0 until data.length()).mapNotNull { index ->
            runCatching { convert(data.getJSONObject(index)) }.getOrNull()
        }
    }
}
