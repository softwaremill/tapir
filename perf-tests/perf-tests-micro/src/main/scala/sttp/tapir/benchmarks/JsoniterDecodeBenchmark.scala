package sttp.tapir.benchmarks

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.openjdk.jmh.annotations.*
import sttp.tapir.*
import sttp.tapir.Schema
import sttp.tapir.json.jsoniter.*
import sttp.tapir.generic.auto.*

import java.util.concurrent.TimeUnit

// --- Domain model ---

enum Color:
  case Red, Green, Blue, Yellow, Black, White

enum Priority:
  case Low, Medium, High, Critical

enum Status:
  case Active, Inactive, Pending, Archived

case class Address(
    street: String,
    city: String,
    zipCode: String,
    country: String
)

case class Tag(
    name: String,
    color: Color
)

case class Item(
    id: Long,
    name: String,
    description: Option[String],
    price: Double,
    quantity: Int,
    active: Boolean,
    tags: List[Tag]
)

case class Order(
    id: Long,
    status: Status,
    priority: Priority,
    items: List[Item],
    shippingAddress: Address,
    billingAddress: Option[Address],
    notes: Option[String],
    totalAmount: Double
)

// --- Schema instances (semi-auto) ---

object DomainSchemas:
  given Schema[Color] = Schema.derivedEnumeration.defaultStringBased
  given Schema[Priority] = Schema.derivedEnumeration.defaultStringBased
  given Schema[Status] = Schema.derivedEnumeration.defaultStringBased
  given Schema[Address] = Schema.derived
  given Schema[Tag] = Schema.derived
  given Schema[Item] = Schema.derived
  given Schema[Order] = Schema.derived

// --- Jsoniter codecs ---

object DomainJsonCodecs:
  given JsonValueCodec[Order] = JsonCodecMaker.makeWithoutDiscriminator

// --- Benchmark ---

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
class JsoniterDecodeBenchmark:

  import DomainSchemas.given
  import DomainJsonCodecs.given

  private val tapirCodec: Codec[String, Order, CodecFormat.Json] = jsoniterCodec[Order]

  private val sampleOrder: Order = Order(
    id = 12345L,
    status = Status.Active,
    priority = Priority.High,
    items = List(
      Item(
        id = 1L,
        name = "Widget A",
        description = Some("A fine widget"),
        price = 29.99,
        quantity = 3,
        active = true,
        tags = List(Tag("sale", Color.Red), Tag("new", Color.Green))
      ),
      Item(
        id = 2L,
        name = "Gadget B",
        description = None,
        price = 49.50,
        quantity = 1,
        active = true,
        tags = List(Tag("premium", Color.Blue))
      ),
      Item(
        id = 3L,
        name = "Gizmo C",
        description = Some("Budget option"),
        price = 9.99,
        quantity = 10,
        active = false,
        tags = Nil
      )
    ),
    shippingAddress = Address("123 Main St", "Springfield", "62704", "US"),
    billingAddress = Some(Address("456 Oak Ave", "Shelbyville", "62705", "US")),
    notes = Some("Please deliver before noon"),
    totalAmount = 189.37
  )

  // Generate JSON using jsoniter itself so the format is always consistent
  private val orderJson: String = writeToString(sampleOrder)

  /** Decode using the full Tapir codec (jsoniter deserialization + schema validation). */
  @Benchmark
  def decodeTapirCodec(): DecodeResult[Order] =
    tapirCodec.decode(orderJson)

  /** Decode using only jsoniter (no Tapir schema validation). */
  @Benchmark
  def decodeJsoniterOnly(): Order =
    readFromString[Order](orderJson)
