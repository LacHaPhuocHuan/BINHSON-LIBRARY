package com.library.binhson.borrowingservice.service.Impl;

import com.library.binhson.borrowingservice.dto.BookDto;
import com.library.binhson.borrowingservice.dto.BorrowingSessionDto;
import com.library.binhson.borrowingservice.dto.DataPage;
import com.library.binhson.borrowingservice.entity.*;
import com.library.binhson.borrowingservice.repository.*;
import com.library.binhson.borrowingservice.service.IBorrowingSessionService;
import com.library.binhson.borrowingservice.utils.AuthUtils;
import jakarta.ws.rs.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class BorrowingSessionServiceImpl implements IBorrowingSessionService {
    private final BorrowingSessionRepository sessionRepository;
    private final BorrowingDetailRepository detailRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final ModelMapper modelMapper;
    private final PersonRepository personRepository;


    @Override
    public DataPage get(int offset, int limit) {
        var dp=new DataPage(limit,offset, Arrays.asList(getAllDtos()));
        return dp;
    }

    @Override
    public BorrowingSessionDto get(String id){
        try {
            return findDtoById(Long.parseLong(id));
        }catch (Exception ex){
            throw new BadRequestException("Id is incorrect");
        }
    }

    @Override
    public DataPage search(HashMap<String, String> map) {
        String key=map.get("keyword");
        //todo
        var list=getAllDtos();
        var result=list.stream().filter(
                dto -> dto.getBorrowingType().name().contains(key)
                || dto.getMember().getUsername().contains(key)
                || dto.getStartDate().toDateTime().toString().contains(key)
        ).toList();
        return new DataPage(result.size(), 1, Arrays.asList(result));
    }

    @Override
    public void deleteById(String id) {
        try {
            sessionRepository.deleteById(Long.parseLong(id));
        }catch (Exception ex){
            throw new BadRequestException("Id is incorrect");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BorrowingSessionDto addNewSession(List<String> bookIds, String memberId) {
        var books=bookRepository.findAllById(bookIds);
        Member member=memberRepository.findById(memberId).orElseThrow(()->new BadRequestException("Member don't exist."));
        BorrowingSession session=BorrowingSession.builder()
                .borrowingType(BorrowingType.OnReal)
                .startDate(new DateTime())
                .estimateBookReturnDate((new DateTime()).plusDays(7))
                .librarian((Librarian)getCurrentUser())
                .build();
        session= sessionRepository.save(session);
        //borrowing detail
        saveDetails(books, session);
        return map(session);
    }

    private Person getCurrentUser() {
        return personRepository.findByUsername(AuthUtils.getUsername()).orElseThrow(()->new RuntimeException("Loi tai server ( sai vo)"));
    }

    private void saveDetails(List<Book> books, BorrowingSession session) {
        for (Book b: books
             ) {
            BorrowingDetailId borrowingDetailId=BorrowingDetailId.builder()
                    .borrowingSessionId(session.getId())
                    .bookId(b.getId()).build();
            BorrowingDetail borrowingDetail=BorrowingDetail.builder()
                    .borrowingDetailId(borrowingDetailId)
                    .borrowingSession(session)
                    .book(b)
                    .build();
            detailRepository.save(borrowingDetail);
        }
    }


    @Override
    public BorrowingSessionDto renewSession(Long id) {
        BorrowingSession bs=findById(id);
        if(AuthUtils.isAdminOrLibrarianPermission())
            bs.setEstimateBookReturnDate(bs.getEstimateBookReturnDate().plusDays(7));
        else if(bs.getMember().getUsername().equals(AuthUtils.getUsername()))
            bs.setEstimateBookReturnDate(bs.getEstimateBookReturnDate().plusDays(7));
        else throw new BadRequestException("You don't have permission for this method.");
        bs=sessionRepository.save(bs);
        return map(bs);
    }

    @Cacheable(value = "borrowing-sessions")
    private List<BorrowingSession> getAll(){
       return sessionRepository.findAll();
    }

    private BorrowingSession findById(Long id){
        return getAll().stream().filter(session -> session.getId().equals(id)).findFirst().orElseThrow(()->new BadRequestException("Id is incorrect"));
    }

    private  List<BorrowingSessionDto> getAllDtos(){
        List<BorrowingSession> bss=getAll();
        return bss.stream().map(bs->map(bs)).collect(Collectors.toList());
    }
    private BorrowingSessionDto map(BorrowingSession bs){
        BorrowingSessionDto dto=modelMapper.map(bs, BorrowingSessionDto.class);
        List<Book> books=detailRepository.findByBorrowingSession(bs);
        var bookDtos=books.stream().map(book -> modelMapper.map(book, BookDto.class)).toList();
        dto.setBookDtos(bookDtos);
        return dto;
    }
    private  BorrowingSessionDto findDtoById(Long id){
        var bs=findById(id);
        return map(bs);
    }



}
