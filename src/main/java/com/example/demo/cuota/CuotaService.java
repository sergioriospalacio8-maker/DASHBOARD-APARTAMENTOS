package com.example.demo.cuota;

import static jakarta.persistence.LockModeType.NONE;
import static jakarta.persistence.LockModeType.PESSIMISTIC_READ;
import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CuotaService {
    private static final BigDecimal MAXIMO = new BigDecimal("9999999999.99");
    private static final Pattern MONEDA = Pattern.compile("[A-Z]{3}");
    private static final Pattern REFERENCIA = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @PersistenceContext
    private EntityManager em;

    record VistaCuota(Long id, Long apartamentoId, BigDecimal importe, String moneda, BigDecimal saldo) {}
    record VistaPago(Long id, Long cuotaId, BigDecimal importe, String referencia) {}
    record ResultadoPago(VistaPago pago, boolean nuevo) {}

    @Transactional
    public VistaCuota crear(long apartamentoId, BigDecimal importe, String moneda) {
        BigDecimal exacto = importeValido(importe);
        if (moneda == null || !MONEDA.matcher(moneda).matches()) throw new ResponseStatusException(BAD_REQUEST);
        exigirApartamento(apartamentoId, PESSIMISTIC_READ);
        Cuota cuota = new Cuota(apartamentoId, exacto, moneda);
        em.persist(cuota);
        return new VistaCuota(cuota.id, apartamentoId, exacto, moneda, exacto);
    }

    @Transactional(readOnly = true)
    public List<VistaCuota> listar(Long apartamentoId) {
        if (apartamentoId == null) return resumen("", null);
        exigirApartamento(apartamentoId, NONE);
        return resumen("WHERE c.apartamentoId = :id", apartamentoId);
    }

    @Transactional(readOnly = true)
    public VistaCuota consultar(long id) { return resumenDe(idValido(id)); }

    @Transactional(readOnly = true)
    public List<VistaPago> pagos(long id) {
        exigirCuota(id, NONE);
        return em.createQuery("SELECT p FROM Pago p WHERE p.cuotaId = :id ORDER BY p.id", Pago.class)
                .setParameter("id", id).getResultList().stream().map(CuotaService::vista).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoPago registrar(long id, BigDecimal importe, String referencia) {
        BigDecimal exacto = importeValido(importe);
        if (!REFERENCIA.matcher(referencia).matches()) throw new ResponseStatusException(BAD_REQUEST);
        // Toda alta bloquea primero la misma cuota; las lecturas ven los pagos confirmados tras esperar.
        exigirCuota(id, PESSIMISTIC_WRITE);
        Pago existente = em.createQuery("SELECT p FROM Pago p WHERE p.cuotaId = :id AND p.referencia = :ref", Pago.class)
                .setParameter("id", id).setParameter("ref", referencia).getSingleResultOrNull();
        if (existente != null) {
            if (existente.importe.compareTo(exacto) != 0) throw new ResponseStatusException(CONFLICT);
            return new ResultadoPago(vista(existente), false);
        }
        if (exacto.compareTo(resumenDe(id).saldo()) > 0) throw new ResponseStatusException(CONFLICT);
        Pago pago = new Pago(id, exacto, referencia);
        em.persist(pago);
        return new ResultadoPago(vista(pago), true);
    }

    private VistaCuota resumenDe(long id) {
        return resumen("WHERE c.id = :id", id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    private List<VistaCuota> resumen(String filtro, Long id) {
        var consulta = em.createQuery("""
                SELECT c.id, c.apartamentoId, c.importe, c.moneda, c.importe - COALESCE(SUM(p.importe), 0)
                FROM Cuota c LEFT JOIN Pago p ON p.cuotaId = c.id
                """ + filtro + " GROUP BY c.id, c.apartamentoId, c.importe, c.moneda ORDER BY c.id", VistaCuota.class);
        if (id != null) consulta.setParameter("id", id);
        return consulta.getResultList();
    }

    private void exigirCuota(long id, LockModeType bloqueo) {
        if (em.find(Cuota.class, idValido(id), bloqueo) == null) throw new ResponseStatusException(NOT_FOUND);
    }

    private void exigirApartamento(long id, LockModeType bloqueo) {
        // Sólo necesita la PK existente; no depende de la migración de torres.
        if (em.createQuery("SELECT a.id FROM Apartamento a WHERE a.id = :id", Long.class)
                .setParameter("id", idValido(id)).setLockMode(bloqueo).getResultList().isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND);
        }
    }

    private static long idValido(long id) {
        if (id < 1L) throw new ResponseStatusException(BAD_REQUEST);
        return id;
    }

    private static BigDecimal importeValido(BigDecimal importe) {
        if (importe == null || importe.signum() <= 0 || importe.scale() > 2 || importe.compareTo(MAXIMO) > 0) {
            throw new ResponseStatusException(BAD_REQUEST);
        }
        return importe.setScale(2);
    }

    private static VistaPago vista(Pago pago) {
        return new VistaPago(pago.id, pago.cuotaId, pago.importe, pago.referencia);
    }
}
