package co.edu.uniquindio.sga.domain.port;

import co.edu.uniquindio.sga.domain.exception.ImagenNoAlmacenadaException;
import co.edu.uniquindio.sga.domain.valueobject.FormatoImagen;

/**
 * Puerto del servicio que guarda las fotos de los apartamentos y las publica en una URL. El adaptador se elige con
 * sga.imagenes.proveedor: Cloudinary (servicio externo) o local (disco del servidor, sin internet ni credenciales).
 * El dominio solo conoce la URL que devuelve; esa URL es la que se guarda en la Imagen del apartamento.
 */
public interface AlmacenImagenes {

    /**
     * @param contenido los bytes de la foto, ya validada como JPEG, PNG o WebP.
     * @param formato   el formato reconocido en el contenido.
     * @return la URL pública con la que se muestra la foto.
     * @throws ImagenNoAlmacenadaException si el servicio no responde, tarda más de lo permitido o rechaza la foto.
     */
    String guardar(byte[] contenido, FormatoImagen formato);
}
