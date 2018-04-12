package nl.quintor.studybits.student.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.Prover;
import nl.quintor.studybits.student.entities.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class StudentProverService {

    private MetaWalletService metaWalletService;
    private IndyPool indyPool;

    public Prover getProver(Student student) throws Exception {
        IndyWallet indyWallet = metaWalletService.createIndyWalletFromMetaWallet(student.getMetaWallet());

        return new Prover(student.getUserName(), indyPool, indyWallet, student.getUserName());

    }
}

