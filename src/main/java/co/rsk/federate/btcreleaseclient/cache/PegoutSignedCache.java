package co.rsk.federate.btcreleaseclient.cache;

import co.rsk.crypto.Keccak256;

public interface PegoutSignedCache {

  /**
   * Checks if the specified RSK transaction hash for pegout creation has already
   * been signed.
   * 
   * @param pegoutCreationRskTxHash The Keccak256 hash of the RSK transaction for
   *                                pegout creation.
   * @return {@code true} if the transaction hash has already been signed,
   *         {@code false} otherwise.
   */
  boolean hasAlreadyBeenSigned(Keccak256 pegoutCreationRskTxHash);

  /**
   * Stores the specified RSK transaction hash for pegout creation along with its
   * timestamp in the cache.
   * 
   * @param pegoutCreationRskTxHash The Keccak256 hash of the RSK transaction for
   *                                pegout creation to be stored.
   */
  void put(Keccak256 pegoutCreationRskTxHash);
}
