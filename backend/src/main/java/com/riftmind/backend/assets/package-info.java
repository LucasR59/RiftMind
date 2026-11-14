/**
 * Copie locale des données et images du jeu (Data Dragon, et emblèmes de rang livrés avec le backend), servie par le
 * backend sous {@code /api/assets/} : le navigateur ne dépend plus des CDN de Riot.
 *
 * <ul>
 *   <li>{@link com.riftmind.backend.assets.AssetPath} : liste blanche des chemins servis (rien d'autre n'est lu ni
 *       téléchargé).</li>
 *   <li>{@link com.riftmind.backend.assets.AssetSync} : copie chaque nouvelle version de Data Dragon, et ne la publie
 *       qu'une fois complète.</li>
 *   <li>{@link com.riftmind.backend.assets.AssetService} : sert les fichiers copiés ; seules les icônes de profil
 *       (des milliers) sont téléchargées à la première demande.</li>
 * </ul>
 * Voir le README, section « Images du jeu ».
 */
package com.riftmind.backend.assets;
