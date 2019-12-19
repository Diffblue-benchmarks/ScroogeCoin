import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {

	private UTXOPool utxoPool;

	/* Creates a public ledger whose current UTXOPool (collection of unspent 
	 * transaction outputs) is utxoPool. This should make a defensive copy of 
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		this.utxoPool = new UTXOPool(utxoPool);
	}

	/* Returns true if 
	 * (1) all outputs claimed by tx are in the current UTXO pool, 
	 * (2) the signatures on each input of tx are valid, 
	 * (3) no UTXO is claimed multiple times by tx, 
	 * (4) all of tx’s output values are non-negative, and
	 * (5) the sum of tx’s input values is greater than or equal to the sum of   
	        its output values;
	   and false otherwise.
	 */
	public boolean isValidTx(Transaction tx) {
		// Deze hashset wordt gebruikt voor controles op het dubbel opeisen van UTXO's
		Set<UTXO> claimedUTXO = new HashSet<>();
		double inputSum = 0, outputSum = 0;

		List<Transaction.Input> inputs = tx.getInputs();
		for (int i = 0; i < inputs.size(); i ++) {
			Transaction.Input input = inputs.get(i);
			UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
			Transaction.Output correspondingOutput = utxoPool.getTxOutput(utxo); // Pak de output die bij de UTXO hoort

			// Controleer of de UTXO in de pool/wallet aanwezig is
			if (!utxoPool.contains(utxo)) {
				return false;
			}
			// Controleer of de public key van de ontvanger de signature van de verzender kan verifieren
			if (!correspondingOutput.address.verifySignature(tx.getRawDataToSign(i), input.signature)) {
				return false;
			}
			// Controleer of de utxo al niet eerder in de transactie is opgeeisd (double spending attack)
			if (!claimedUTXO.add(utxo)) {
				return false;
			}
			inputSum += correspondingOutput.value;
		}

		List<Transaction.Output> outputs = tx.getOutputs();
		for (int i = 0; i < outputs.size(); i ++) {
			Transaction.Output output = outputs.get(i);
			// Controleer op negatieve waarden, je kan niet negatief geld overhouden
			if (output.value < 0) {
				return false;
			}
			outputSum += output.value;
		}
		// Controleer op dat de outputsom niet de inputsum overschrijdt (inclusief transactiekosten)
		return outputSum < inputSum;
	}

	/* Handles each epoch by receiving an unordered array of proposed 
	 * transactions, checking each transaction for correctness, 
	 * returning a mutually valid array of accepted transactions, 
	 * and updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		List<Transaction> acceptedTx = new ArrayList<>();
		for (Transaction transaction : possibleTxs) {
			if (isValidTx(transaction)) {
				for (Transaction.Input input : transaction.getInputs()) {
					UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
					utxoPool.removeUTXO(utxo); // Verwijder de meest recente UTXO, dus degene van de vorige transactie
				}
				for (int i = 0; i < transaction.getOutputs().size(); i ++) {
					Transaction.Output output = transaction.getOutput(i);
					UTXO utxo = new UTXO(transaction.getHash(), i);
					utxoPool.addUTXO(utxo, output); // De nieuwe UTXO's worden gebaseerd op de meest recente outputs
				}
				acceptedTx.add(transaction);
			}
		}
		return acceptedTx.toArray(new Transaction[acceptedTx.size()]);
	}
} 
