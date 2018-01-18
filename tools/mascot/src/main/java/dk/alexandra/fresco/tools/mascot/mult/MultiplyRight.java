package dk.alexandra.fresco.tools.mascot.mult;

import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.util.Pair;
import dk.alexandra.fresco.framework.util.StrictBitVector;
import dk.alexandra.fresco.tools.mascot.MascotResourcePool;
import dk.alexandra.fresco.tools.mascot.TwoPartyProtocol;
import dk.alexandra.fresco.tools.mascot.field.FieldElement;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Right hand side of a two-party protocol for computing a secret sharing of a the entry wise
 * product of vectors <i><b>a</b></i> held by the <i>left</i> party and <i><b>b</b></i> held by the
 * <i>right</i> party.
 *
 * <p>
 * This protocol is a generalization of step 2 of the <i>Multiply</i> sub-protocol in the
 * <i>&Pi;<sub>Triple</sub></i> protocol of the MASCOT paper. While step 2 of <i>Multiply</i>
 * computes a secret sharing of a <i>scalar product</i>, this implementation computes the entry wise
 * product. To compute a scalar product using this implementation we can let all entries of
 * <i><b>b</b></i> be equal. This also allows us to compute multiple scalar products in a single
 * batch letting <i><b>b</b></i> be the concatenation of vectors with equal entries.
 * </p>
 * <p>
 * <b>Note:</b> this class is to be used as a sub-protocol in
 * {@link dk.alexandra.fresco.tools.mascot.triple.TripleGeneration} and may not be secure if used
 * outside of the intended context.
 * </p>
 * <p>
 * This class implements the functionality of the right party. For the other side, see
 * {@link MultiplyLeft}. The resulting entry wise product is secret-shared among the two parties.
 * </p>
 */
public class MultiplyRight extends TwoPartyProtocol {

  private final MultiplyRightHelper multiplyRightHelper;

  public MultiplyRight(MascotResourcePool resourcePool, Network network, Integer otherId,
      int numLeftFactors) {
    super(resourcePool, network, otherId);
    multiplyRightHelper = new MultiplyRightHelper(resourcePool, network, otherId, numLeftFactors);
  }

  public MultiplyRight(MascotResourcePool resourcePool, Network network, Integer otherId) {
    this(resourcePool, network, otherId, 1);
  }

  /**
   * Runs a batch of the entry wise product protocol with a given of right hand vector.
   *
   * <p>
   * For right vector <i><b>b</b>= b<sub>0</sub>, b<sub>1</sub>, ...)</i> and left vector of the
   * other party <i><b>a</b> = (a<sub>0</sub>, a<sub>1</sub>, ...)</i>, the protocol computes secret
   * shares of entry wise product <i>(a<sub>0</sub>b<sub>0</sub>, a<sub>1</sub>b<sub>1</sub>, ...
   * </i>).
   * </p>
   *
   * @param rightFactors this party's vector <i>b<sub>0</sub>, b<sub>1</sub> ...</i>
   * @return shares of the products <i>a<sub>0</sub>b<sub>0</sub>, a<sub>1</sub>b<sub>1</sub> ...
   *         </i>
   */
  public List<FieldElement> multiply(List<FieldElement> rightFactors) {
    List<Pair<StrictBitVector, StrictBitVector>> seedPairs =
        multiplyRightHelper.generateSeeds(rightFactors.size(), getModBitLength());
    // convert seeds pairs to field elements so we can compute on them
    List<Pair<FieldElement, FieldElement>> feSeedPairs =
        seedsToFieldElements(seedPairs, getModulus(), getModBitLength());
    // compute q0 - q1 + b for each seed pair
    List<FieldElement> diffs = multiplyRightHelper.computeDiffs(feSeedPairs, rightFactors);
    // send diffs over to other party
    getNetwork().send(getOtherId(), getFieldElementSerializer().serialize(diffs));
    // get zero index seeds
    List<FieldElement> feZeroSeeds =
        feSeedPairs.stream().map(Pair::getFirst).collect(Collectors.toList());
    // compute product shares
    return multiplyRightHelper.computeProductShares(feZeroSeeds, rightFactors.size());
  }

  private List<Pair<FieldElement, FieldElement>> seedsToFieldElements(
      List<Pair<StrictBitVector, StrictBitVector>> seedPairs, BigInteger modulus,
      int modBitLength) {
    return seedPairs.stream().map(pair -> {
      FieldElement t0 = fromBits(pair.getFirst(), modulus, modBitLength);
      FieldElement t1 = fromBits(pair.getSecond(), modulus, modBitLength);
      return new Pair<>(t0, t1);
    }).collect(Collectors.toList());
  }

  private FieldElement fromBits(StrictBitVector vector, BigInteger modulus, int modBitLength) {
    // TODO need to check somewhere that the modulus is close enough to 2^modBitLength
    return new FieldElement(new BigInteger(vector.toByteArray()).mod(modulus), modulus,
        modBitLength);
  }

}
